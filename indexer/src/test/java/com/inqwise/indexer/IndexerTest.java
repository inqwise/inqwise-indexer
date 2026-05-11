package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
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
	void deleteShouldCleanupAllResources(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
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
			repository,
			store,
			new IndexerOptions(),
			event -> {
				if (event.getType() == IndexerEventType.INDEXER_STOPPED) {
					stoppedEventEmitted.set(true);
				}
				return Future.succeededFuture();
			}
		);

		// Setup state
		repository
			.save(model)
			.compose(ignored ->
				store.put(
					"test_index",
					"1",
					new JsonObject().put("key", "value")
				)
			)
			.compose(ignored -> indexer.activate())
			.compose(ignored -> indexer.delete())
			.onComplete(
				testContext.succeeding(deletedModel ->
					testContext.verify(() -> {
						assertEquals(
							model.getIndexName(),
							deletedModel.getIndexName()
						);

						// Verify repository deletion
						repository
							.get(1)
							.onComplete(
								testContext.succeeding(opt ->
									testContext.verify(() -> {
										assertFalse(
											opt.isPresent(),
											"Model should be removed from repository"
										);

										// Verify document store drop
										assertNull(
											store.get("test_index", "1"),
											"Document store should be dropped"
										);

										// Verify event emission
										assertTrue(
											stoppedEventEmitted.get(),
											"INDEXER_STOPPED event should be emitted"
										);

										testContext.completeNow();
									})
								)
							);
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
						assertNull(
							store.get("test_index", "1"),
							"Document store should be dropped even without repository"
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
	void deleteShouldNotAttemptRepositoryDeleteIfIdIsNull(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();

		// Model with no ID
		IndexerModel model = IndexerModel.builder()
			.withTargetName("test")
			.withIndexName("test_index")
			.build();

		Indexer indexer = new Indexer(
			vertx,
			model,
			null, // queue
			repository,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

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
	void deleteShouldFailWhenQueueDeleteFailsAndSkipLaterResources(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TestIndexerQueue queue = new TestIndexerQueue();
		queue.deleteFailure = new IllegalStateException("queue delete failed");
		TestDocumentStore store = new TestDocumentStore();
		TestRepository repository = new TestRepository();
		Indexer indexer = new Indexer(
			vertx,
			modelWithId(),
			queue,
			repository,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		indexer.delete()
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("queue delete failed", error.getMessage());
				assertTrue(queue.deleteCalled);
				assertFalse(store.dropCalled);
				assertFalse(repository.deleteCalled);
				testContext.completeNow();
			})));
	}

	@Test
	void deleteShouldFailWhenDocumentDropFailsAndSkipRepositoryDelete(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TestIndexerQueue queue = new TestIndexerQueue();
		TestDocumentStore store = new TestDocumentStore();
		store.dropFailure = new IllegalStateException("document drop failed");
		TestRepository repository = new TestRepository();
		Indexer indexer = new Indexer(
			vertx,
			modelWithId(),
			queue,
			repository,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		indexer.delete()
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("document drop failed", error.getMessage());
				assertTrue(queue.deleteCalled);
				assertTrue(store.dropCalled);
				assertFalse(repository.deleteCalled);
				testContext.completeNow();
			})));
	}

	@Test
	void deleteShouldFailWhenRepositoryDeleteFails(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TestIndexerQueue queue = new TestIndexerQueue();
		TestDocumentStore store = new TestDocumentStore();
		TestRepository repository = new TestRepository();
		repository.deleteFailure = new IllegalStateException("repository delete failed");
		Indexer indexer = new Indexer(
			vertx,
			modelWithId(),
			queue,
			repository,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		indexer.delete()
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("repository delete failed", error.getMessage());
				assertTrue(queue.deleteCalled);
				assertTrue(store.dropCalled);
				assertTrue(repository.deleteCalled);
				testContext.completeNow();
			})));
	}

	@Test
	void deleteShouldSucceedWhenRepositoryRecordIsAlreadyMissing(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TestIndexerQueue queue = new TestIndexerQueue();
		TestDocumentStore store = new TestDocumentStore();
		TestRepository repository = new TestRepository();
		repository.deleteResult = false;
		IndexerModel model = modelWithId();
		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			repository,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		indexer.delete()
			.onComplete(testContext.succeeding(deleted -> testContext.verify(() -> {
				assertEquals(model.getId(), deleted.getId());
				assertTrue(queue.deleteCalled);
				assertTrue(store.dropCalled);
				assertTrue(repository.deleteCalled);
				testContext.completeNow();
			})));
	}

	@Test
	void closeShouldCloseQueueWithoutDeletingQueueOrDroppingIndex(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TestIndexerQueue queue = new TestIndexerQueue();
		TestDocumentStore store = new TestDocumentStore();
		Indexer indexer = new Indexer(
			vertx,
			modelWithId(),
			queue,
			null,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		indexer.close()
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(queue.closeCalled);
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

	private static class TestIndexerQueue implements IndexerQueue {
		private boolean closeCalled;
		private boolean deleteCalled;
		private Throwable closeFailure;
		private Throwable deleteFailure;

		@Override
		public Future<Void> publish(IndexerActionItem item) {
			return Future.succeededFuture();
		}

		@Override
		public Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options) {
			return Future.failedFuture("consumer is not expected in this test");
		}

		@Override
		public Future<Void> close() {
			closeCalled = true;
			return closeFailure == null ? Future.succeededFuture() : Future.failedFuture(closeFailure);
		}

		@Override
		public Future<Void> delete() {
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

	private static class TestRepository implements IndexerRepository {
		private boolean deleteCalled;
		private boolean deleteResult = true;
		private Throwable deleteFailure;

		@Override
		public Future<Integer> save(IndexerModel model) {
			return Future.succeededFuture(model.getId());
		}

		@Override
		public Future<Optional<IndexerModel>> get(Integer id) {
			return Future.succeededFuture(Optional.empty());
		}

		@Override
		public Future<List<IndexerModel>> getByTargetId(Integer targetId) {
			return Future.succeededFuture(List.of());
		}

		@Override
		public Future<List<IndexerModel>> list() {
			return Future.succeededFuture(List.of());
		}

		@Override
		public Future<Boolean> delete(Integer id) {
			deleteCalled = true;
			return deleteFailure == null ? Future.succeededFuture(deleteResult) : Future.failedFuture(deleteFailure);
		}
	}
}
