package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.CatchUpBarrierActionItem;
import com.inqwise.indexer.CompleteIndexActionItem;
import com.inqwise.indexer.Indexer;
import com.inqwise.indexer.IndexerEventPublisher;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerModel;
import com.inqwise.indexer.IndexerOptions;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.InMemoryIndexerQueue;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoadIndexerMarkerHandlerTest {
	@Test
	void completeActionItemMarksLoadHistoricalComplete(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_1")
			.withRole(IndexerRole.LOAD_WRITER)
			.build();
		CompleteIndexActionItem item = CompleteIndexActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.build();

		loads.insert(new InsertIndexerLoad(
			20,
			10,
			null,
			LiveWriterPolicy.NONE,
			"default",
			IndexerLoadState.HISTORICAL_LOADING,
			Instant.parse("2026-05-28T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> {
			Indexer indexer = new Indexer(
				vertx,
				model,
				queue,
				new InMemoryIndexerDocumentStore(),
				new IndexerOptions(),
				IndexerEventPublisher.NOOP,
				new LoadIndexerMarkerHandler(
					loads,
					IndexerLifecycleEventBus.NOOP,
					new CapturingCommandService()
				)
			);

			return indexer.activate()
				.compose(activated -> queue.publisher("customers_1"))
				.compose(publisher -> publisher.publish(item));
		}).compose(ignored -> loads.getByIndexerId(20))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerLoadState.HISTORICAL_COMPLETE, found.get().state());
				testContext.completeNow();
			})));
	}

	@Test
	void completeActionItemAutoPublishesHistoricalOnlyLoad(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingCommandService commands = new CapturingCommandService();
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_1")
			.withRole(IndexerRole.LOAD_WRITER)
			.build();
		CompleteIndexActionItem item = CompleteIndexActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.build();

		loads.insert(new InsertIndexerLoad(
			20,
			10,
			null,
			LiveWriterPolicy.NONE,
			"default",
			IndexerLoadState.HISTORICAL_LOADING,
			Instant.parse("2026-05-28T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> {
			Indexer indexer = new Indexer(
				vertx,
				model,
				queue,
				new InMemoryIndexerDocumentStore(),
				new IndexerOptions(),
				IndexerEventPublisher.NOOP,
				new LoadIndexerMarkerHandler(loads, IndexerLifecycleEventBus.NOOP, commands)
			);

			return indexer.activate()
				.compose(activated -> queue.publisher("customers_1"))
				.compose(publisher -> publisher.publish(item));
		}).onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
			PublishLoadCommand published = new PublishLoadCommand(commands.command.toJson());
			assertEquals(20, published.getIndexerId());
			assertEquals(1L, published.getExpectedLoadVersion());
			testContext.completeNow();
		})));
	}

	@Test
	void reviewedHistoricalOnlyLoadWaitsForReview(VertxTestContext testContext) {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingCommandService commands = new CapturingCommandService();
		LoadIndexerMarkerHandler handler = new LoadIndexerMarkerHandler(
			loads,
			IndexerLifecycleEventBus.NOOP,
			commands
		);
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_load")
			.withRole(IndexerRole.LOAD_WRITER)
			.build();
		CompleteIndexActionItem item = CompleteIndexActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.build();

		loads.insert(new InsertIndexerLoad(
			20,
			10,
			null,
			LiveWriterPolicy.NONE,
			"default",
			IndexerLoadState.HISTORICAL_LOADING,
			Instant.parse("2026-05-28T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			true
		)).compose(ignored -> handler.complete(model, item))
			.compose(ignored -> loads.getByIndexerId(20))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerLoadState.WAITING_FOR_REVIEW, found.get().state());
				assertTrue(commands.commands.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void completeActionItemRequestsStableCatchUpBarrierForLinkedLiveWriter(
		VertxTestContext testContext
	) {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingCommandService commands = new CapturingCommandService();
		LoadIndexerMarkerHandler handler = new LoadIndexerMarkerHandler(
			loads,
			IndexerLifecycleEventBus.NOOP,
			commands
		);
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_load")
			.withRole(IndexerRole.LOAD_WRITER)
			.build();
		CompleteIndexActionItem item = CompleteIndexActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.build();

		loads.insert(new InsertIndexerLoad(
			20,
			10,
			21,
			LiveWriterPolicy.CREATE_IMMEDIATELY,
			"default",
			IndexerLoadState.HISTORICAL_LOADING,
			Instant.parse("2026-05-28T10:00:00Z"),
			Instant.parse("2026-05-28T09:55:00Z"),
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> handler.complete(model, item))
			.compose(ignored -> handler.complete(model, item))
			.compose(ignored -> loads.getByIndexerId(20))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerLoadState.CATCH_UP_BARRIER_REQUESTED, found.get().state());
				assertEquals(2, commands.commands.size());
				CatchUpBarrierActionItem first = submittedBarrier(commands.commands.get(0));
				CatchUpBarrierActionItem second = submittedBarrier(commands.commands.get(1));
				assertEquals(21, first.getIndexerId());
				assertEquals(first.getBarrierId(), second.getBarrierId());
				assertEquals(first.getBarrierTimestamp(), second.getBarrierTimestamp());
				testContext.completeNow();
			})));
	}

	@Test
	void catchUpBarrierMarksLinkedLoadReady(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		Instant barrierTimestamp = Instant.parse("2026-05-28T10:30:00Z");
		IndexerModel model = IndexerModel.builder()
			.withId(21)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_1")
			.withRole(IndexerRole.LIVE_WRITER)
			.build();
		CatchUpBarrierActionItem item = CatchUpBarrierActionItem.builder()
			.withTargetId(10)
			.withIndexerId(21)
			.withBarrierId("barrier-1")
			.withBarrierTimestamp(barrierTimestamp)
			.build();

		loads.insert(new InsertIndexerLoad(
			20,
			10,
			21,
			LiveWriterPolicy.CREATE_IMMEDIATELY,
			"default",
			IndexerLoadState.HISTORICAL_COMPLETE,
			Instant.parse("2026-05-28T10:00:00Z"),
			Instant.parse("2026-05-28T09:55:00Z"),
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> loads.requestBarrier(new RequestIndexerLoadBarrier(
			20,
			"barrier-1",
			barrierTimestamp,
			0L
		))).compose(ignored -> {
			Indexer indexer = new Indexer(
				vertx,
				model,
				queue,
				new InMemoryIndexerDocumentStore(),
				new IndexerOptions(),
				IndexerEventPublisher.NOOP,
				new LoadIndexerMarkerHandler(
					loads,
					IndexerLifecycleEventBus.NOOP,
					new CapturingCommandService()
				)
			);

			return indexer.activate()
				.compose(activated -> queue.publisher("customers_1"))
				.compose(publisher -> publisher.publish(item));
		}).compose(ignored -> loads.getByIndexerId(20))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerLoadState.CATCH_UP_READY, found.get().state());
				assertEquals("barrier-1", found.get().lastBarrierId());
				assertEquals(barrierTimestamp, found.get().lastBarrierTimestamp());
				testContext.completeNow();
			})));
	}

	private static CatchUpBarrierActionItem submittedBarrier(Command command) {
		SubmitIndexActionsCommand submit = new SubmitIndexActionsCommand(
			command.toJson(),
			command.getCorrelationId()
		);
		return (CatchUpBarrierActionItem) submit.getActions().get(0);
	}

	private static class CapturingCommandService implements CommandService {
		private final List<Command> commands = new ArrayList<>();
		private Command command;

		@Override
		public Future<Void> submit(Command command) {
			this.command = command;
			commands.add(command);
			return Future.succeededFuture();
		}
	}
}
