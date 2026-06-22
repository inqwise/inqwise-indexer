package com.inqwise.indexer.service.action;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerActionItems;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerQueueConsumer;
import com.inqwise.indexer.IndexerQueueConsumerOptions;
import com.inqwise.indexer.IndexerQueuePublisher;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.RoutedIndexActionPublisher;
import com.inqwise.indexer.hot.HotIndexActionsRequest;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.hot.HotIndexer;
import com.inqwise.indexer.hot.HotMetadataView;
import com.inqwise.indexer.hot.HotTarget;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ExtendWith(VertxExtension.class)
class TargetActionServiceVerticleTest {
	@Test
	void submitsTargetActionsThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		RecordingHotIndexActionsService hotActions = new RecordingHotIndexActionsService();

		vertx.deployVerticle(new TargetActionServiceVerticle(hotActions))
			.compose(ignored -> TargetActionServices.proxy(vertx).submit(new TargetActionSubmitRequest()
				.setSubmissionId("submit-1")
				.setTargetName("customers")
				.setTimestamp(Instant.parse("2026-06-06T10:15:00Z"))
				.setActions(List.of(IndexerActionItems.putDocument(
					"42",
					new JsonObject().put("name", "Ada")
				)))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("submit-1", result.getSubmissionId());
				assertEquals(TargetActionSubmitState.ACCEPTED, result.getState());
				assertEquals("customers", hotActions.request.get().targetName());
				assertEquals(1, hotActions.request.get().actions().size());
				testContext.completeNow();
			})));
	}

	private static class RecordingHotIndexActionsService extends HotIndexActionsService {
		private final AtomicReference<HotIndexActionsRequest> request = new AtomicReference<>();

		private RecordingHotIndexActionsService() {
			super(new EmptyHotMetadataView(), new RoutedIndexActionPublisher(new NoopQueue()), new NoopCommandService());
		}

		@Override
		public Future<Void> submit(HotIndexActionsRequest request) {
			this.request.set(request);
			return Future.succeededFuture();
		}
	}

	private static class EmptyHotMetadataView implements HotMetadataView {
		@Override
		public Optional<HotTarget> findTargetByName(String targetName) {
			return Optional.empty();
		}

		@Override
		public Optional<HotIndexer> findIndexerById(Integer indexerId) {
			return Optional.empty();
		}

		@Override
		public Future<Void> refreshHotTargetByConcreteTargetId(Integer targetId) {
			return Future.succeededFuture();
		}

		@Override
		public void invalidateHotTargetByConcreteTargetId(Integer targetId) {
		}

		@Override
		public void invalidateHotTargetByIndexerId(Integer indexerId) {
		}

		@Override
		public void invalidateAllHotTargets() {
		}
	}

	private static class NoopQueue implements IndexerQueueClient {
		@Override
		public Future<IndexerQueuePublisher> publisher(String queueName) {
			return Future.failedFuture("not used");
		}

		@Override
		public Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options) {
			return Future.failedFuture("not used");
		}
	}

	private static class NoopCommandService implements CommandService {
		@Override
		public Future<Void> submit(Command command) {
			return Future.failedFuture("not used");
		}
	}
}
