package com.inqwise.indexer.operations.queues;

import static com.inqwise.indexer.testing.TestMetadataRecords.indexerRecord;
import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerLifecycleProviderSignal;
import com.inqwise.indexer.lifecycle.IndexerLifecycleSubscription;
import com.inqwise.indexer.lifecycle.TargetMetadataChanged;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.cleanup.CleanupResetIndexerQueueCommandHandler;
import com.inqwise.indexer.adapters.local.InMemoryCommandEngine;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerQueueManagementServiceTest {
	@Test
	void resetQueueAdvancesQueueNameAndPublishesLifecycle(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(
				repository,
				"queue-customers-1",
				IndexerRuntimeState.ACTIVE
			))
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1",
				0L
			))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-customers-1-v1", found.get().queueName());
				assertEquals(1L, found.get().version());
				assertEquals(List.of("queue-customers-1-v1"), resources.ensured);
				assertEquals(List.of("queue-customers-1"), resources.deleted);
				assertEquals(1, events.size());
				assertEquals("indexer.queue.reset", events.get(0).getCommandType());
				assertEquals(1L, events.get(0).getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueDoesNotReloadMetadataAfterMutation(VertxTestContext testContext) {
		CountingIndexerReadsRepository repository = new CountingIndexerReadsRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1",
				0L
			)).map(indexerId))
			.onComplete(testContext.succeeding(indexerId -> testContext.verify(() -> {
				assertEquals(1, repository.indexerReads);
				assertEquals(List.of("queue-customers-1"), resources.deleted);
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueRejectsVersionOverflowBeforeProvisioning(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(
			repository,
			new InMemoryIndexerLifecycleEventBus(),
			resources
		);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1",
				Long.MAX_VALUE
			)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals(
					"Invalid expected version for indexer queue reset: " + Long.MAX_VALUE,
					error.getMessage()
				);
				assertTrue(resources.ensured.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueuePreservesCustomSuffixThatDoesNotMatchMetadataVersion(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1-v3", IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1-v3",
				0L
			))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-customers-1-v3-v1", found.get().queueName());
				assertEquals(List.of("queue-customers-1-v3-v1"), resources.ensured);
				assertEquals(List.of("queue-customers-1-v3"), resources.deleted);
				testContext.completeNow();
			})));
	}

	@Test
	void consecutiveResetsAdvanceVersionedQueueWithoutReusingRetiredNames(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1",
				0L
			)).compose(ignored -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1-v1",
				1L
			))).compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-customers-1-v2", found.get().queueName());
				assertEquals(2L, found.get().version());
				assertEquals(
					List.of("queue-customers-1-v1", "queue-customers-1-v2"),
					resources.ensured
				);
				assertEquals(
					List.of("queue-customers-1", "queue-customers-1-v1"),
					resources.deleted
				);
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueFailsOnExpectedVersionMismatchBeforeEnsure(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1",
				5L
			)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("queue state conflict"));
				assertTrue(resources.ensured.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueRedeliveryReturnsAppliedResultWithoutAdvancingAgain(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(
				repository,
				"queue-customers-1",
				IndexerRuntimeState.ACTIVE
			))
			.compose(indexerId -> {
				ResetIndexerQueueRequest request = new ResetIndexerQueueRequest(
					indexerId,
					"queue-customers-1",
					0L
				);
				return service.reset(request)
					.compose(ignored -> service.reset(request))
					.compose(ignored -> repository.getIndexerById(indexerId));
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-customers-1-v1", found.get().queueName());
				assertEquals(1L, found.get().version());
				assertEquals(List.of("queue-customers-1-v1"), resources.ensured);
				assertEquals(List.of("queue-customers-1", "queue-customers-1"), resources.deleted);
				assertEquals(2, events.size());
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueRedeliveryRetriesCleanupAfterMetadataAdvanced(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		resources.deleteFailuresRemaining = 1;
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> {
				ResetIndexerQueueRequest request = new ResetIndexerQueueRequest(
					indexerId,
					"queue-customers-1",
					0L
				);
				return service.reset(request)
					.recover(error -> repository.getIndexerById(indexerId).compose(found -> {
						assertTrue(found.isPresent());
						assertEquals("queue-customers-1-v1", found.get().queueName());
						assertEquals(1L, found.get().version());
						return service.reset(request);
					}))
					.compose(ignored -> repository.getIndexerById(indexerId));
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-customers-1-v1", found.get().queueName());
				assertEquals(List.of("queue-customers-1-v1"), resources.ensured);
				assertEquals(List.of("queue-customers-1", "queue-customers-1"), resources.deleted);
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueAvoidsReusingCurrentCustomVersionSuffix(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1-v1", IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1-v1",
				0L
			)).compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-customers-1-v1-v1", found.get().queueName());
				assertEquals(List.of("queue-customers-1-v1-v1"), resources.ensured);
				assertEquals(List.of("queue-customers-1-v1"), resources.deleted);
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueFailsWhenIndexerIsDeleted(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(
				repository,
				"queue-customers-1",
				IndexerRuntimeState.NON_ACTIVE,
				MutationState.DELETING
			)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1",
				0L
			)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("Cannot reset deleted indexer queue"));
				assertTrue(resources.ensured.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueLeavesMetadataUnchangedWhenEnsureFails(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		resources.ensureFailure = new IllegalStateException("topic create failed");
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1",
				0L
			))
				.recover(error -> repository.getIndexerById(indexerId).compose(found -> {
					assertTrue(found.isPresent());
					assertEquals("queue-customers-1", found.get().queueName());
					assertEquals(0L, found.get().version());
					return Future.failedFuture(error);
				})))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("topic create failed", error.getMessage());
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueSucceedsWhenLifecyclePublishFailsAfterMetadataUpdate(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		FailingLifecycleEventBus eventBus = new FailingLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1",
				0L
			))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-customers-1-v1", found.get().queueName());
				assertEquals(1L, found.get().version());
				assertEquals(1, eventBus.publishAttempts);
				testContext.completeNow();
			})));
	}

	@Test
	void resetNonActiveIndexerUpdatesQueueWithoutActivatingRuntime(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		IndexerQueueManagementService service = service(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeState.NON_ACTIVE)
			.compose(indexerId -> service.reset(new ResetIndexerQueueRequest(
				indexerId,
				"queue-customers-1",
				0L
			))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerRuntimeState.NON_ACTIVE, found.get().runtimeState());
				assertEquals("queue-customers-1-v1", found.get().queueName());
				assertEquals(List.of("queue-customers-1-v1"), resources.ensured);
				testContext.completeNow();
			})));
	}

	private IndexerQueueManagementService service(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus,
		IndexerQueueResourceManager resources
	) {
		InMemoryCommandEngine commands = new InMemoryCommandEngine();
		commands.register(new CleanupResetIndexerQueueCommandHandler(resources));
		return new MetadataIndexerQueueManagementService(
			repository,
			TestMetadataChangeNotifiers.create(eventBus),
			resources,
			commands
		);
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		String queueName,
		IndexerRuntimeState runtimeState
	) {
		return insertIndexer(repository, queueName, runtimeState, MutationState.WRITABLE);
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		String queueName,
		IndexerRuntimeState runtimeState,
		MutationState mutationState
	) {
		return repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"test",
				targetId,
				"customers",
				"customers_1",
				queueName,
				IndexerType.INDEX,
				runtimeState,
				PublicationState.UNPUBLISHED,
				mutationState
			)));
	}

	private static class RecordingQueueResourceManager implements IndexerQueueResourceManager {
		private final List<String> ensured = new ArrayList<>();
		private final List<String> deleted = new ArrayList<>();
		private Throwable ensureFailure;
		private int deleteFailuresRemaining;

		@Override
		public Future<Void> ensure(String queueName) {
			ensured.add(queueName);
			return ensureFailure == null
				? Future.succeededFuture()
				: Future.failedFuture(ensureFailure);
		}

		@Override
		public Future<Void> delete(String queueName) {
			deleted.add(queueName);
			if (deleteFailuresRemaining > 0) {
				deleteFailuresRemaining--;
				return Future.failedFuture("topic delete failed");
			}
			return Future.succeededFuture();
		}
	}

	private static class CountingIndexerReadsRepository
		extends InMemoryDocumentStoreMetadataRepository {
		private int indexerReads;

		@Override
		public Future<Optional<IndexerRecord>> getIndexerById(Integer id) {
			indexerReads++;
			return super.getIndexerById(id);
		}
	}

	private static class FailingLifecycleEventBus implements IndexerLifecycleEventBus {
		private int publishAttempts;

		@Override
		public Future<Void> publish(IndexerMetadataChanged event) {
			publishAttempts++;
			return Future.failedFuture("lifecycle publish failed");
		}

		@Override
		public Future<Void> publish(TargetMetadataChanged event) {
			return Future.succeededFuture();
		}

		@Override
		public Future<IndexerLifecycleSubscription> subscribe(
			Handler<IndexerMetadataChanged> handler
		) {
			return Future.succeededFuture(IndexerLifecycleSubscription.NOOP);
		}

		@Override
		public Future<IndexerLifecycleSubscription> subscribeTarget(
			Handler<TargetMetadataChanged> handler
		) {
			return Future.succeededFuture(IndexerLifecycleSubscription.NOOP);
		}

		@Override
		public Future<IndexerLifecycleSubscription> subscribeProviderSignals(
			Handler<IndexerLifecycleProviderSignal> handler
		) {
			return Future.succeededFuture(IndexerLifecycleSubscription.NOOP);
		}
	}
}
