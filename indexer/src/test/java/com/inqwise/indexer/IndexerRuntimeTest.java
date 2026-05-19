package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.inqwise.indexer.commands.ActivateIndexerCommand;
import com.inqwise.indexer.commands.ActivateIndexerCommandHandler;
import com.inqwise.indexer.commands.DeactivateIndexerCommand;
import com.inqwise.indexer.commands.DeactivateIndexerCommandHandler;
import com.inqwise.indexer.commands.DeleteIndexerCommand;
import com.inqwise.indexer.commands.DeleteIndexerCommandHandler;
import com.inqwise.indexer.commands.InMemoryCommandService;
import com.inqwise.indexer.metadata.DeleteIndexer;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRuntimeStatus;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.UpdateIndexerQueueName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerRuntimeTest {
	@Test
	void activateCommandStartsLocalRuntimeIndexer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		AtomicInteger started = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			indexer -> new Indexer(
				vertx,
				IndexerRuntime.toModel(indexer),
				new InMemoryIndexerQueue(),
				new InMemoryIndexerDocumentStore(),
				new IndexerOptions(),
				event -> {
					if (event.getType() == IndexerEventType.INDEXER_STARTED) {
						started.incrementAndGet();
					}

					return Future.succeededFuture();
				}
			)
		);

		insertIndexer(repository, IndexerRuntimeStatus.NON_ACTIVE, MutationState.WRITABLE)
			.compose(id -> runtime.start()
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(id)))
				.compose(ignored -> repository.getIndexerById(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerRuntimeStatus.STARTED, found.orElseThrow().runtimeStatus());
				assertEquals(1L, found.get().version());
				assertEquals(1, started.get());
				testContext.completeNow();
			})));
	}

	@Test
	void deactivateCommandClosesLocalRuntimeIndexer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		AtomicInteger stopped = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			indexer -> new Indexer(
				vertx,
				IndexerRuntime.toModel(indexer),
				new InMemoryIndexerQueue(),
				new InMemoryIndexerDocumentStore(),
				new IndexerOptions(),
				event -> {
					if (event.getType() == IndexerEventType.INDEXER_STOPPED) {
						stopped.incrementAndGet();
					}

					return Future.succeededFuture();
				}
			)
		);

		insertIndexer(repository, IndexerRuntimeStatus.STARTED, MutationState.WRITABLE)
			.compose(id -> runtime.start()
				.compose(ignored -> runtime.reconcile(id))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(id)))
				.compose(ignored -> runtime.reconcile(id))
				.compose(ignored -> repository.getIndexerById(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerRuntimeStatus.NON_ACTIVE, found.orElseThrow().runtimeStatus());
				assertEquals(1L, found.get().version());
				assertEquals(1, stopped.get());
				testContext.completeNow();
			})));
	}

	@Test
	void runtimeConstructorDeploysVerticleBackedIndexer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerDocumentStore documentStore = new InMemoryIndexerDocumentStore();
		IndexerRuntime runtime = new IndexerRuntime(
			vertx,
			repository,
			eventBus,
			queue,
			documentStore,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		insertIndexer(repository, IndexerRuntimeStatus.STARTED, MutationState.WRITABLE)
			.compose(id -> runtime.reconcile(id))
			.compose(ignored -> queue.publisher("queue-customers-1"))
			.compose(publisher -> publisher.publish(PutDocumentActionItem.builder()
				.withTargetId(1)
				.withIndexerId(1)
				.withIndexName("customers_1")
				.withUid("42")
				.withDocument(new io.vertx.core.json.JsonObject().put("name", "Ada"))
				.build()).eventually(publisher::close))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals("Ada", documentStore.get("customers_1", "42").getString("name"));
				testContext.completeNow();
			})));
	}

	@Test
	void verticleBackedIndexerCanReactivateAfterClose(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerDocumentStore documentStore = new InMemoryIndexerDocumentStore();
		IndexerRuntime runtime = new IndexerRuntime(
			vertx,
			repository,
			eventBus,
			queue,
			documentStore,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		insertIndexer(repository, IndexerRuntimeStatus.STARTED, MutationState.WRITABLE)
			.compose(id -> runtime.reconcile(id)
				.compose(ignored -> runtime.close(id))
				.compose(ignored -> runtime.reconcile(id)))
			.compose(ignored -> queue.publisher("queue-customers-1"))
			.compose(publisher -> publisher.publish(PutDocumentActionItem.builder()
				.withTargetId(1)
				.withIndexerId(1)
				.withIndexName("customers_1")
				.withUid("43")
				.withDocument(new io.vertx.core.json.JsonObject().put("name", "Grace"))
				.build()).eventually(publisher::close))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals("Grace", documentStore.get("customers_1", "43").getString("name"));
				testContext.completeNow();
			})));
	}

	@Test
	void reconcileReplacesLocalIndexerWhenQueueNameChanges(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger activated = new AtomicInteger();
		AtomicInteger unregistered = new AtomicInteger();
		AtomicInteger closed = new AtomicInteger();
		AtomicReference<String> lastQueueName = new AtomicReference<>();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			indexer -> {
				lastQueueName.set(indexer.queueName());
				return new TestIndexer(
					vertx,
					IndexerRuntime.toModel(indexer),
					activated,
					unregistered,
					closed
				);
			}
		);

		insertIndexer(repository, IndexerRuntimeStatus.STARTED, MutationState.WRITABLE)
			.compose(id -> runtime.reconcile(id)
				.compose(ignored -> repository.updateIndexerQueueName(new UpdateIndexerQueueName(
					id,
					"queue-customers-1-v1",
					0L
				)))
				.compose(ignored -> runtime.reconcile(id)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(2, activated.get());
				assertEquals(1, closed.get());
				assertEquals(0, unregistered.get());
				assertEquals("queue-customers-1-v1", lastQueueName.get());
				testContext.completeNow();
			})));
	}

	@Test
	void missingRepositoryRecordClosesLocalRuntimeIndexer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger activated = new AtomicInteger();
		AtomicInteger unregistered = new AtomicInteger();
		AtomicInteger closed = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			indexer -> new TestIndexer(
				vertx,
				IndexerRuntime.toModel(indexer),
				activated,
				unregistered,
				closed
			)
		);

		insertIndexer(repository, IndexerRuntimeStatus.STARTED, MutationState.WRITABLE)
			.compose(id -> runtime.reconcile(id)
				.compose(ignored -> repository.deleteIndexer(new DeleteIndexer(id, 0L)))
				.compose(ignored -> runtime.reconcile(id)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, activated.get());
				assertEquals(0, unregistered.get());
				assertEquals(1, closed.get());
				testContext.completeNow();
			})));
	}

	@Test
	void deleteCommandClosesLocalIndexerBeforeCleaningResources(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		AtomicInteger activated = new AtomicInteger();
		AtomicInteger unregistered = new AtomicInteger();
		AtomicInteger closed = new AtomicInteger();
		AtomicInteger cleaned = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			indexer -> new TestIndexer(
				vertx,
				IndexerRuntime.toModel(indexer),
				activated,
				unregistered,
				closed
			),
			model -> {
				assertEquals(1, closed.get());
				assertEquals("customers_1", model.getIndexName());
				cleaned.incrementAndGet();
				return Future.succeededFuture();
			}
		);

		insertIndexer(repository, IndexerRuntimeStatus.STARTED, MutationState.WRITABLE)
			.compose(id -> runtime.start()
				.compose(ignored -> runtime.reconcile(id))
				.compose(ignored -> commandService.submit(new DeleteIndexerCommand(id, 0L)))
				.compose(ignored -> repository.getIndexerById(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerRuntimeStatus.DELETED, found.orElseThrow().runtimeStatus());
				assertEquals(1, activated.get());
				assertEquals(0, unregistered.get());
				assertEquals(1, closed.get());
				assertEquals(1, cleaned.get());
				testContext.completeNow();
			})));
	}

	@Test
	void deletedReconcileWithoutLocalIndexerStillCleansResources(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger cleaned = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			indexer -> {
				throw new AssertionError("Deleted reconcile must not activate");
			},
			model -> {
				assertEquals("customers_1", model.getIndexName());
				cleaned.incrementAndGet();
				return Future.succeededFuture();
			}
		);

		insertIndexer(repository, IndexerRuntimeStatus.DELETED, MutationState.DELETING)
			.compose(runtime::reconcile)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, cleaned.get());
				testContext.completeNow();
			})));
	}

	@Test
	void cleanerFailureFailsDeletedReconcileForRetry(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger cleaned = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			repository,
			eventBus,
			indexer -> {
				throw new AssertionError("Deleted reconcile must not activate");
			},
			model -> {
				cleaned.incrementAndGet();
				return Future.failedFuture("cleanup failed");
			}
		);

		insertIndexer(repository, IndexerRuntimeStatus.DELETED, MutationState.DELETING)
			.compose(runtime::reconcile)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("cleanup failed"));
				assertEquals(1, cleaned.get());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		return new InMemoryCommandService()
			.register(new ActivateIndexerCommandHandler(repository, eventBus))
			.register(new DeactivateIndexerCommandHandler(repository, eventBus))
			.register(new DeleteIndexerCommandHandler(repository, eventBus));
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerRuntimeStatus runtimeStatus,
		MutationState mutationState
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				runtimeStatus,
				PublicationState.UNPUBLISHED,
				mutationState
			)));
	}

	private static class TestIndexer extends Indexer {
		private final AtomicInteger activated;
		private final AtomicInteger unregistered;
		private final AtomicInteger closed;

		private TestIndexer(
			Vertx vertx,
			IndexerModel model,
			AtomicInteger activated,
			AtomicInteger unregistered,
			AtomicInteger closed
		) {
			super(vertx, model, new InMemoryIndexerDocumentStore());
			this.activated = activated;
			this.unregistered = unregistered;
			this.closed = closed;
		}

		@Override
		public Future<Void> activate() {
			activated.incrementAndGet();
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> unregister() {
			unregistered.incrementAndGet();
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> close() {
			closed.incrementAndGet();
			return Future.succeededFuture();
		}
	}
}
