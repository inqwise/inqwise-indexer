package com.inqwise.indexer.load.workflow;

import com.inqwise.indexer.load.adapters.local.InMemoryIndexerLoadRepository;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;
import com.inqwise.indexer.load.api.LoadCompletion;
import com.inqwise.indexer.load.api.LoadWriter;
import com.inqwise.indexer.load.repository.InsertIndexerLoad;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.actions.CompleteIndexActionItem;
import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.runtime.IndexerQueueClient;
import com.inqwise.indexer.runtime.IndexerQueueConsumer;
import com.inqwise.indexer.runtime.IndexerQueueConsumerOptions;
import com.inqwise.indexer.runtime.IndexerQueuePublisher;
import com.inqwise.indexer.routing.QueueIndexerPublishingService;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.actions.RemoveDocumentActionItem;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class QueueLoadWriterTest {
	@Test
	void submitNormalizesDocumentActionsToLoadWriterIdentity() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		RecordingQueue queue = new RecordingQueue();
		QueueLoadWriter writer = writer(loads, queue);

		var submitted = writer.submit(List.of(
			PutDocumentActionItem.builder()
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build(),
			RemoveDocumentActionItem.builder()
				.withUid("43")
				.build()
		));

		assertTrue(submitted.succeeded());
		assertEquals(2, queue.items.size());
		PutDocumentActionItem put = assertInstanceOf(PutDocumentActionItem.class, queue.items.get(0));
		assertEquals(10, put.getTargetId());
		assertEquals(20, put.getIndexerId());
		assertEquals("customers--idx-load", put.getIndexName());
		assertEquals("42", put.getUid());
		RemoveDocumentActionItem remove = assertInstanceOf(RemoveDocumentActionItem.class, queue.items.get(1));
		assertEquals(10, remove.getTargetId());
		assertEquals(20, remove.getIndexerId());
		assertEquals("customers--idx-load", remove.getIndexName());
		assertEquals("43", remove.getUid());
	}

	@Test
	void submitRejectsMismatchedConcreteAction() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		QueueLoadWriter writer = writer(loads);

		var submitted = writer.submit(List.of(new PutDocumentActionItem(new JsonObject()
			.put(PutDocumentActionItem.TARGET_ID, 11)
			.put(PutDocumentActionItem.UID, "42")
			.put(PutDocumentActionItem.DOCUMENT, new JsonObject()))));

		assertTrue(submitted.failed());
		assertEquals("Action target id mismatch", submitted.cause().getMessage());
	}

	@Test
	void submitRejectsInternalMarkers() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		QueueLoadWriter writer = writer(loads);

		var submitted = writer.submit(List.of(CompleteIndexActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.build()));

		assertTrue(submitted.failed());
		assertEquals(
			"LoadWriter.submit does not accept internal marker action: COMPLETE",
			submitted.cause().getMessage()
		);
	}

	@Test
	void completePublishesMarkerForHistoricalLoadingLoad() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		RecordingQueue queue = new RecordingQueue();
		QueueLoadWriter writer = writer(loads, queue);

		loads.insert(load(IndexerLoadState.HISTORICAL_LOADING));

		var completed = writer.complete(new LoadCompletion("source-audit-1"));

		assertTrue(completed.succeeded());
		assertEquals(1, queue.items.size());
		CompleteIndexActionItem marker =
			assertInstanceOf(CompleteIndexActionItem.class, queue.items.get(0));
		assertEquals(10, marker.getTargetId());
		assertEquals(20, marker.getIndexerId());
	}

	@Test
	void completeDoesNotRepublishMarkerForAlreadyCompletedLoad() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		RecordingQueue queue = new RecordingQueue();
		QueueLoadWriter writer = writer(loads, queue);

		loads.insert(load(IndexerLoadState.HISTORICAL_COMPLETE));

		var completed = writer.complete(new LoadCompletion("source-audit-1"));

		assertTrue(completed.succeeded());
		assertEquals(0, queue.items.size());
	}

	@Test
	void completeDoesNotPublishMarkerForTerminalLoad() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		RecordingQueue queue = new RecordingQueue();
		QueueLoadWriter writer = writer(loads, queue);

		loads.insert(load(IndexerLoadState.CANCELLED));

		var completed = writer.complete(new LoadCompletion("source-audit-1"));

		assertTrue(completed.succeeded());
		assertEquals(0, queue.items.size());
	}

	@Test
	void failMarksActiveLoadFailed() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		QueueLoadWriter writer = writer(loads);

		loads.insert(load(IndexerLoadState.HISTORICAL_LOADING));

		var failed = writer.fail(new IllegalStateException("source unavailable"));

		assertTrue(failed.succeeded());
		var found = loads.getByIndexerId(20).result().orElseThrow();
		assertEquals(IndexerLoadState.FAILED, found.state());
		assertEquals("source unavailable", found.failureReason());
	}

	@Test
	void failDoesNotOverwriteCancelledLoad() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		QueueLoadWriter writer = writer(loads);

		loads.insert(load(IndexerLoadState.CANCELLED));

		var failed = writer.fail(new IllegalStateException("late provider failure"));

		assertTrue(failed.succeeded());
		var found = loads.getByIndexerId(20).result().orElseThrow();
		assertEquals(IndexerLoadState.CANCELLED, found.state());
	}

	@Test
	void failDoesNotOverwritePublishedLoad() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		QueueLoadWriter writer = writer(loads);

		loads.insert(load(IndexerLoadState.PUBLISHED));

		var failed = writer.fail(new IllegalStateException("late provider failure"));

		assertTrue(failed.succeeded());
		var found = loads.getByIndexerId(20).result().orElseThrow();
		assertEquals(IndexerLoadState.PUBLISHED, found.state());
	}

	private QueueLoadWriter writer(InMemoryIndexerLoadRepository loads) {
		return writer(loads, new InMemoryIndexerQueue());
	}

	private QueueLoadWriter writer(InMemoryIndexerLoadRepository loads, IndexerQueueClient queue) {
		return new QueueLoadWriter(
			10,
			20,
			"customers--idx-load",
			"customers--queue-load",
			new QueueIndexerPublishingService(queue),
			loads
		);
	}

	private InsertIndexerLoad load(IndexerLoadState state) {
		return new InsertIndexerLoad(
			20,
			10,
			null,
			LiveWriterPolicy.NONE,
			"default",
			state,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		);
	}

	private static class RecordingQueue implements IndexerQueueClient {
		private final List<IndexerActionItem> items = new ArrayList<>();

		@Override
		public Future<IndexerQueuePublisher> publisher(String queueName) {
			return Future.succeededFuture(new IndexerQueuePublisher() {
				@Override
				public Future<Void> publish(IndexerActionItem item) {
					items.add(item);
					return Future.succeededFuture();
				}

				@Override
				public Future<Void> close() {
					return Future.succeededFuture();
				}
			});
		}

		@Override
		public Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options) {
			return Future.failedFuture("consumer is not expected");
		}
	}
}
