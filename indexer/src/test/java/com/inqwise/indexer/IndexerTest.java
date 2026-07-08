package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class IndexerTest {

	@Test
	void processActionItemAcceptsMatchingConcreteIdentity(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers-2024")
			.withIndexName("customers-2024-a")
			.build();
		Indexer indexer = new Indexer(vertx, model, store);
		PutDocumentActionItem item = IndexerActionItems.concretePutDocument(
			10,
			20,
			"customers-2024-a",
			"42",
			new JsonObject().put("name", "Ada")
		);

		indexer.processActionItem(item)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals("Ada", store.get("customers-2024-a", "42").getString("name"));
				testContext.completeNow();
			})));
	}

	@Test
	void processActionItemRejectsMismatchedConcreteIdentity(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers-2024")
			.withIndexName("customers-2024-a")
			.build();
		Indexer indexer = new Indexer(vertx, model, store);
		PutDocumentActionItem item = IndexerActionItems.concretePutDocument(
			10,
			21,
			"customers-2024-a",
			"42",
			new JsonObject().put("name", "Ada")
		);

		indexer.processActionItem(item)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals(
					"Action indexer id mismatch for indexer: customers-2024-a",
					error.getMessage()
				);
				assertNull(store.get("customers-2024-a", "42"));
				testContext.completeNow();
			})));
	}

	@Test
	void processActionItemRejectsUnroutedDocumentAction(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers-2024")
			.withIndexName("customers-2024-a")
			.build();
		Indexer indexer = new Indexer(vertx, model, store);
		RemoveDocumentActionItem item = IndexerActionItems.removeDocument("42");

		indexer.processActionItem(item)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("Remove document action target id is required", error.getMessage());
				testContext.completeNow();
			})));
	}

	@Test
	void deleteShouldCloseRuntimeWithoutDroppingDocumentStore(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		IndexerModel model = IndexerModel.builder()
			.withId(1)
			.withTargetName("test")
			.withIndexName("test_index")
			.build();

		AtomicBoolean stoppedEventEmitted = new AtomicBoolean(false);

		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			store,
			new IndexerOptions(),
			event -> {
				if (event.getType() == IndexerEventType.INDEXER_STOPPED) {
					stoppedEventEmitted.set(true);
				}
				return Future.succeededFuture();
			}
		);

		store
			.put("test_index", "1", new JsonObject().put("key", "value"))
			.compose(ignored -> indexer.activate())
			.compose(ignored -> indexer.delete())
			.onComplete(
				testContext.succeeding(deletedModel ->
					testContext.verify(() -> {
						assertEquals(
							model.getIndexName(),
							deletedModel.getIndexName()
						);
						assertEquals(
							"value",
							store.get("test_index", "1").getString("key"),
							"Document store cleanup is handled outside Indexer"
						);
						assertTrue(
							stoppedEventEmitted.get(),
							"INDEXER_STOPPED event should be emitted"
						);
						testContext.completeNow();
					})
				)
			);
	}

	@Test
	void deleteShouldWorkWithoutRepository(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		IndexerModel model = IndexerModel.builder()
			.withTargetName("test")
			.withIndexName("test_index")
			.build();

		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		store
			.put("test_index", "1", new JsonObject().put("key", "value"))
			.compose(ignored -> indexer.delete())
			.onComplete(
				testContext.succeeding(deletedModel ->
					testContext.verify(() -> {
						assertEquals(
							model.getIndexName(),
							deletedModel.getIndexName()
						);
						assertEquals(
							"value",
							store.get("test_index", "1").getString("key"),
							"Document store cleanup is handled outside Indexer"
						);
						testContext.completeNow();
					})
				)
			);
	}

	@Test
	void deleteShouldSucceedEvenIfNotActivated(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		IndexerModel model = IndexerModel.builder()
			.withTargetName("test")
			.withIndexName("test_index")
			.build();

		Indexer indexer = new Indexer(vertx, model, store);

		indexer
			.delete()
			.onComplete(
				testContext.succeeding(deletedModel ->
					testContext.verify(() -> {
						assertEquals(
							model.getIndexName(),
							deletedModel.getIndexName()
						);
						testContext.completeNow();
					})
				)
			);
	}

	@Test
	void deleteShouldNotDeleteQueueOrDropDocumentStore(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TestIndexerQueue queue = new TestIndexerQueue();
		TestDocumentStore store = new TestDocumentStore();
		Indexer indexer = new Indexer(
			vertx,
			modelWithId(),
			queue,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		indexer.delete()
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertFalse(queue.deleteCalled);
				assertFalse(store.dropCalled);
				testContext.completeNow();
			})));
	}

	@Test
	void deleteShouldIgnoreDocumentDropBecauseCleanupIsExternal(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TestIndexerQueue queue = new TestIndexerQueue();
		TestDocumentStore store = new TestDocumentStore();
		Indexer indexer = new Indexer(
			vertx,
			modelWithId(),
			queue,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		indexer.delete()
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertFalse(queue.deleteCalled);
				assertFalse(store.dropCalled);
				testContext.completeNow();
			})));
	}

	@Test
	void closeShouldCloseLocalHandlesWithoutDeletingQueueOrDroppingIndex(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TestIndexerQueue queue = new TestIndexerQueue();
		TestDocumentStore store = new TestDocumentStore();
		Indexer indexer = new Indexer(
			vertx,
			modelWithId(),
			queue,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		indexer.close()
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertFalse(queue.closeCalled);
				assertFalse(queue.deleteCalled);
				assertFalse(store.dropCalled);
				testContext.completeNow();
			})));
	}

	private IndexerModel modelWithId() {
		return IndexerModel.builder()
			.withId(1)
			.withTargetName("test")
			.withIndexName("test_index")
			.build();
	}

	private static class TestIndexerQueue implements IndexerQueueClient, IndexerQueueResourceManager {
		private boolean closeCalled;
		private boolean deleteCalled;
		private Throwable closeFailure;
		private Throwable deleteFailure;

		@Override
		public Future<IndexerQueuePublisher> publisher(String queueName) {
			return Future.succeededFuture(new IndexerQueuePublisher() {
				@Override
				public Future<Void> publish(IndexerActionItem item) {
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
			return Future.failedFuture("consumer is not expected in this test");
		}

		public Future<Void> close() {
			closeCalled = true;
			return closeFailure == null ? Future.succeededFuture() : Future.failedFuture(closeFailure);
		}

		@Override
		public Future<Void> ensure(String queueName) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> delete(String queueName) {
			deleteCalled = true;
			return deleteFailure == null ? Future.succeededFuture() : Future.failedFuture(deleteFailure);
		}
	}

	private static class TestDocumentStore implements IndexerDocumentStore {
		private boolean dropCalled;
		private Throwable dropFailure;

		@Override
		public Future<Void> put(String indexName, String uid, JsonObject document) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> remove(String indexName, String uid) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> drop(String indexName) {
			dropCalled = true;
			return dropFailure == null ? Future.succeededFuture() : Future.failedFuture(dropFailure);
		}
	}

}
