package com.inqwise.indexer.runtime;

import static com.inqwise.indexer.testing.TestMetadataRecords.indexerRecord;
import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;

import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBusProvider;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.providers.IndexerPlugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.inqwise.indexer.cleanup.DeleteIndexerCommand;
import com.inqwise.indexer.cleanup.DeleteIndexerCommandHandler;
import com.inqwise.indexer.cleanup.CleanupDeletingIndexerCommandHandler;
import com.inqwise.indexer.adapters.local.InMemoryCommandEngine;
import com.inqwise.indexer.metadata.FinalizeIndexerDeletion;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeStateRequest;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerManagementService;
import com.inqwise.indexer.metadata.UpdateIndexerQueueName;
import com.inqwise.indexer.routing.RoutedIndexActions;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerOperations;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

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
		InMemoryCommandEngine commandService = commandService(repository, eventBus);
		AtomicInteger started = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
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

		insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE, MutationState.WRITABLE)
			.compose(id -> new IndexerRuntimeReconciler(vertx, repository, eventBus, runtime).start()
				.compose(ignored -> new MetadataIndexerManagementService(
					repository,
					TestMetadataChangeNotifiers.create(eventBus)
				).activate(new IndexerRuntimeStateRequest(id, 0L)))
				.compose(ignored -> repository.getIndexerById(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerRuntimeState.ACTIVE, found.orElseThrow().runtimeState());
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
		InMemoryCommandEngine commandService = commandService(repository, eventBus);
		AtomicInteger stopped = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
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

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(id -> new IndexerRuntimeReconciler(vertx, repository, eventBus, runtime).start()
				.compose(ignored -> reconcile(runtime, repository, id))
				.compose(ignored -> new MetadataIndexerManagementService(
					repository,
					TestMetadataChangeNotifiers.create(eventBus)
				).deactivate(new IndexerRuntimeStateRequest(id, 0L)))
				.compose(ignored -> reconcile(runtime, repository, id))
				.compose(ignored -> repository.getIndexerById(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerRuntimeState.NON_ACTIVE, found.orElseThrow().runtimeState());
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
			queue,
			documentStore,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(id -> reconcile(runtime, repository, id))
			.compose(ignored -> queue.publisher("queue-customers-1"))
			.compose(publisher -> publisher.publish(IndexerActionItems.concretePutDocument(
				1,
				1,
				"customers_1",
				"42",
				new io.vertx.core.json.JsonObject().put("name", "Ada")
			)).eventually(publisher::close))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals("Ada", documentStore.get("customers_1", "42").getString("name"));
				testContext.completeNow();
			})));
	}

	@Test
	void verticleBackedRuntimeInvokesAfterCommitHook(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerDocumentStore documentStore = new InMemoryIndexerDocumentStore();
		AtomicReference<DocumentActionExecutionContext> committed = new AtomicReference<>();
		DocumentActionRuntimeHooks hooks = new DocumentActionRuntimeHooks() {
			@Override
			public Future<Void> afterCommit(DocumentActionExecutionContext context) {
				committed.set(context);
				return Future.succeededFuture();
			}
		};
		IndexerRuntime runtime = new IndexerRuntime(
			vertx,
			queue,
			documentStore,
			new IndexerOptions(),
			event -> {
				if (event.getType() == IndexerEventType.CONSUMER_RESUMED
					&& event.getItem() != null) {
					testContext.verify(() -> {
						assertEquals("42", committed.get().documentUid());
						assertEquals(
							"Ada",
							documentStore.get("customers_1", "42").getString("name")
						);
						testContext.completeNow();
					});
				}
				return Future.succeededFuture();
			},
			IndexerPlugins.empty(),
			hooks
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(id -> reconcile(runtime, repository, id))
			.compose(ignored -> queue.publisher("queue-customers-1"))
			.compose(publisher -> publisher.publish(IndexerActionItems.concretePutDocument(
				1,
				1,
				"customers_1",
				"42",
				new io.vertx.core.json.JsonObject().put("name", "Ada")
			)).eventually(publisher::close))
			.onFailure(testContext::failNow);
	}

	@Test
	void runtimePublishingServicePublishesThroughActiveLocalIndexer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerDocumentStore documentStore = new InMemoryIndexerDocumentStore();
		IndexerRuntime runtime = new IndexerRuntime(
			vertx,
			queue,
			documentStore,
			new IndexerOptions(),
			event -> {
				if (event.getType() == IndexerEventType.CONSUMER_RESUMED
					&& event.getItem() != null) {
					testContext.verify(() -> {
						assertEquals(
							"Ada",
							documentStore.get("customers_1", "42").getString("name")
						);
						testContext.completeNow();
					});
				}
				return Future.succeededFuture();
			}
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(id -> reconcile(runtime, repository, id)
				.compose(ignored -> new RuntimeIndexerPublishingService(runtime).publish(List.of(
					RoutedIndexActions.builder()
						.withIndexerId(id)
						.withTargetId(1)
						.withIndexerVersion(0L)
						.withQueueName("queue-customers-1")
						.withActions(List.of(IndexerActionItems.concretePutDocument(
							1,
							id,
							"customers_1",
							"42",
							new io.vertx.core.json.JsonObject().put("name", "Ada")
						)))
						.build()
				))))
			.onFailure(testContext::failNow);
	}

	@Test
	void runtimePublishingServiceFailsWhenIndexerIsNotActiveLocally(
		VertxTestContext testContext
	) {
		IndexerRuntime runtime = new IndexerRuntime(indexer -> {
			throw new AssertionError("No indexer should be created");
		});

		new RuntimeIndexerPublishingService(runtime).publish(List.of(RoutedIndexActions.builder()
			.withIndexerId(99)
			.withTargetId(1)
			.withIndexerVersion(0L)
			.withQueueName("queue-customers-1")
			.withActions(List.of(IndexerActionItems.concretePutDocument(
				1,
				99,
				"customers_1",
				"42",
				new io.vertx.core.json.JsonObject()
			)))
			.build()))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("Runtime indexer is not active locally: 99", error.getMessage());
				testContext.completeNow();
			})));
	}

	@Test
	void runtimePublishingServiceFailsWhenRouteVersionIsStale(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerRuntime runtime = new IndexerRuntime(
			indexer -> new TestIndexer(
				vertx,
				IndexerRuntime.toModel(indexer),
				new AtomicInteger(),
				new AtomicInteger(),
				new AtomicInteger()
			)
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(id -> repository.updateIndexerQueueName(new UpdateIndexerQueueName(
				id,
				"queue-customers-1-v1",
				0L
			)).compose(ignored -> repository.getIndexerById(id))
				.compose(found -> runtime.reconcile(found.orElseThrow()))
				.compose(ignored -> new RuntimeIndexerPublishingService(runtime).publish(List.of(
					RoutedIndexActions.builder()
						.withIndexerId(id)
						.withTargetId(1)
						.withIndexerVersion(0L)
						.withQueueName("queue-customers-1-v1")
						.withActions(List.of(IndexerActionItems.concretePutDocument(
							1,
							id,
							"customers_1",
							"42",
							new io.vertx.core.json.JsonObject()
						)))
						.build()
				))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals(
					"Routed indexer version does not match runtime indexer: expected 0 but was 1",
					error.getMessage()
				);
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
			queue,
			documentStore,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(id -> reconcile(runtime, repository, id)
				.compose(ignored -> runtime.close(id))
				.compose(ignored -> reconcile(runtime, repository, id)))
			.compose(ignored -> queue.publisher("queue-customers-1"))
			.compose(publisher -> publisher.publish(IndexerActionItems.concretePutDocument(
				1,
				1,
				"customers_1",
				"43",
				new io.vertx.core.json.JsonObject().put("name", "Grace")
			)).eventually(publisher::close))
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

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(id -> reconcile(runtime, repository, id)
				.compose(ignored -> repository.updateIndexerQueueName(new UpdateIndexerQueueName(
					id,
					"queue-customers-1-v1",
					0L
				)))
				.compose(ignored -> reconcile(runtime, repository, id)))
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
			indexer -> new TestIndexer(
				vertx,
				IndexerRuntime.toModel(indexer),
				activated,
				unregistered,
				closed
			)
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(id -> reconcile(runtime, repository, id)
				.compose(ignored -> repository.updateIndexerMutationState(
					new com.inqwise.indexer.metadata.UpdateIndexerMutationState(
						id,
						MutationState.DELETING,
						0L
					)
				))
				.compose(ignored -> repository.finalizeIndexerDeletion(
					new FinalizeIndexerDeletion(id, 1L)
				))
				.compose(ignored -> reconcile(runtime, repository, id)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, activated.get());
				assertEquals(0, unregistered.get());
				assertEquals(1, closed.get());
				testContext.completeNow();
			})));
	}

	@Test
	void deleteCommandClosesLocalIndexer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandEngine commandService = commandService(repository, eventBus);
		AtomicInteger activated = new AtomicInteger();
		AtomicInteger unregistered = new AtomicInteger();
		AtomicInteger closed = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			indexer -> new TestIndexer(
				vertx,
				IndexerRuntime.toModel(indexer),
				activated,
				unregistered,
				closed
			)
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(id -> new IndexerRuntimeReconciler(vertx, repository, eventBus, runtime).start()
				.compose(ignored -> reconcile(runtime, repository, id))
				.compose(ignored -> commandService.submit(new DeleteIndexerCommand(id, 0L)))
				.compose(ignored -> repository.getIndexerById(id)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isEmpty());
				assertEquals(1, activated.get());
				assertEquals(0, unregistered.get());
				assertEquals(1, closed.get());
				testContext.completeNow();
			})));
	}

	@Test
	void deletingReconcileWithoutLocalIndexerIsNoop(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		IndexerRuntime runtime = new IndexerRuntime(
			indexer -> {
				throw new AssertionError("Deleted reconcile must not activate");
			}
		);

		insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE, MutationState.DELETING)
			.compose(id -> reconcile(runtime, repository, id))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void stoppedRuntimeDoesNotReceiveLaterMetadataEvents(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger created = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(
			indexer -> {
				created.incrementAndGet();
				return new Indexer(
					vertx,
					IndexerRuntime.toModel(indexer),
					new InMemoryIndexerDocumentStore()
				);
			}
		);
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, MutationState.WRITABLE)
			.compose(indexerId -> reconciler.start()
				.compose(ignored -> reconciler.stop())
				.compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
					indexerId,
					1,
					"test",
					0L
				))))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, created.get());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandEngine commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		InMemoryCommandEngine commandService = new InMemoryCommandEngine();
		return commandService
			.register(new CleanupDeletingIndexerCommandHandler(
				repository,
				IndexerQueueResourceManager.NOOP,
				IndexerDocumentIndexResourceManager.NOOP
			))
			.register(new DeleteIndexerCommandHandler(
				new MetadataIndexerOperations(
					repository,
					TestMetadataChangeNotifiers.create(eventBus)
				),
				commandService
			));
	}

	private Future<Void> reconcile(
		IndexerRuntime runtime,
		InMemoryDocumentStoreMetadataRepository repository,
		Integer indexerId
	) {
		return repository.getIndexerById(indexerId)
			.compose(found -> found
				.map(runtime::reconcile)
				.orElseGet(() -> runtime.close(indexerId)));
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerRuntimeState runtimeStatus,
		MutationState mutationState
	) {
		return repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"test",
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
