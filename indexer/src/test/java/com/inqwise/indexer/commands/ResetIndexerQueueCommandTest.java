package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerLifecycleChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRuntimeStatus;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ResetIndexerQueueCommandTest {
	@Test
	void resetQueueAdvancesQueueNameAndPublishesLifecycle(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		InMemoryCommandService commandService = commandService(repository, eventBus, resources);
		List<IndexerLifecycleChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(
				repository,
				"queue-customers-1",
				IndexerRuntimeStatus.STARTED
			))
			.compose(indexerId -> commandService.submit(new ResetIndexerQueueCommand(indexerId, 0L))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-customers-1-v1", found.get().queueName());
				assertEquals(1L, found.get().version());
				assertEquals(List.of("queue-customers-1-v1"), resources.ensured);
				assertEquals(1, events.size());
				assertEquals(ResetIndexerQueueCommand.TYPE, events.get(0).getCommandType());
				assertEquals(1L, events.get(0).getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueStripsExistingVersionSuffix(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		InMemoryCommandService commandService = commandService(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1-v3", IndexerRuntimeStatus.STARTED)
			.compose(indexerId -> commandService.submit(new ResetIndexerQueueCommand(indexerId, 0L))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("queue-customers-1-v1", found.get().queueName());
				assertEquals(List.of("queue-customers-1-v1"), resources.ensured);
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueFailsOnExpectedVersionMismatchBeforeEnsure(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		InMemoryCommandService commandService = commandService(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeStatus.STARTED)
			.compose(indexerId -> commandService.submit(new ResetIndexerQueueCommand(indexerId, 5L)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("version conflict"));
				assertTrue(resources.ensured.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void resetQueueFailsWhenIndexerIsDeleted(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		InMemoryCommandService commandService = commandService(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeStatus.DELETED)
			.compose(indexerId -> commandService.submit(new ResetIndexerQueueCommand(indexerId, 0L)))
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
		InMemoryCommandService commandService = commandService(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeStatus.STARTED)
			.compose(indexerId -> commandService.submit(new ResetIndexerQueueCommand(indexerId, 0L))
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
	void resetQueueFailsWhenLifecyclePublishFailsAfterMetadataUpdate(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		FailingLifecycleEventBus eventBus = new FailingLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		InMemoryCommandService commandService = commandService(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeStatus.STARTED)
			.compose(indexerId -> commandService.submit(new ResetIndexerQueueCommand(indexerId, 0L))
				.recover(error -> repository.getIndexerById(indexerId).compose(found -> {
					assertTrue(found.isPresent());
					assertEquals("queue-customers-1-v1", found.get().queueName());
					assertEquals(1L, found.get().version());
					return Future.failedFuture(error);
				})))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("lifecycle publish failed", error.getMessage());
				testContext.completeNow();
			})));
	}

	@Test
	void resetNonActiveIndexerUpdatesQueueWithoutActivatingRuntime(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResourceManager resources = new RecordingQueueResourceManager();
		InMemoryCommandService commandService = commandService(repository, eventBus, resources);

		insertIndexer(repository, "queue-customers-1", IndexerRuntimeStatus.NON_ACTIVE)
			.compose(indexerId -> commandService.submit(new ResetIndexerQueueCommand(indexerId, 0L))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerRuntimeStatus.NON_ACTIVE, found.get().runtimeStatus());
				assertEquals("queue-customers-1-v1", found.get().queueName());
				assertEquals(List.of("queue-customers-1-v1"), resources.ensured);
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus,
		IndexerQueueResourceManager resources
	) {
		return new InMemoryCommandService()
			.register(new ResetIndexerQueueCommandHandler(repository, eventBus, resources));
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		String queueName,
		IndexerRuntimeStatus runtimeStatus
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				queueName,
				IndexerType.INDEX,
				runtimeStatus,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)));
	}

	private static class RecordingQueueResourceManager implements IndexerQueueResourceManager {
		private final List<String> ensured = new ArrayList<>();
		private Throwable ensureFailure;

		@Override
		public Future<Void> ensure(String queueName) {
			ensured.add(queueName);
			return ensureFailure == null
				? Future.succeededFuture()
				: Future.failedFuture(ensureFailure);
		}

		@Override
		public Future<Void> delete(String queueName) {
			return Future.succeededFuture();
		}
	}

	private static class FailingLifecycleEventBus implements IndexerLifecycleEventBus {
		@Override
		public Future<Void> publish(IndexerLifecycleChanged event) {
			return Future.failedFuture("lifecycle publish failed");
		}

		@Override
		public Future<Void> subscribe(Handler<IndexerLifecycleChanged> handler) {
			return Future.succeededFuture();
		}
	}
}
