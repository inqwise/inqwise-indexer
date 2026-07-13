package com.inqwise.indexer.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertManifest;
import com.inqwise.indexer.metadata.InsertPublication;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.ManifestStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.ReadinessState;
import com.inqwise.indexer.metadata.UpdateIndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerOperations;
import com.inqwise.indexer.catalog.indexers.MarkIndexerDeletingRequest;
import com.inqwise.indexer.commands.InMemoryCommandEngine;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class CleanupDeletingIndexerCommandTest {
	@Test
	void ownerCleanupDeletesResourcesAndDependentMetadata(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingQueueResources queues = new RecordingQueueResources();
		RecordingIndexResources indexes = new RecordingIndexResources();
		InMemoryCommandEngine commands = commandService(repository, queues, indexes);

		insertIndexer(repository, IndexResourceOwnership.OWNER)
			.compose(indexer -> repository.insertPublication(new InsertPublication(
				null,
				indexer.indexerId(),
				indexer.targetId(),
				"customers",
				"customers-index",
				ReadinessState.READY,
				null
			)).compose(publicationId -> repository.insertManifest(new InsertManifest(
				null,
				indexer.targetId(),
				indexer.indexerId(),
				"customers",
				"customers-index",
				"customers",
				"v1",
				new JsonObject(),
				ManifestStatus.ACTIVE
			)).compose(manifestId -> commands.submit(new DeleteIndexerCommand(
				indexer.indexerId(),
				0L
			)).compose(ignored -> repository.getIndexerById(indexer.indexerId())
				.compose(found -> repository.getPublicationById(publicationId)
					.compose(publication -> repository.getManifestById(manifestId)
						.map(manifest -> new CleanupResult(found, publication, manifest))))))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertTrue(result.indexer().isEmpty());
				assertTrue(result.publication().isEmpty());
				assertTrue(result.manifest().isEmpty());
				assertEquals(List.of("customers-queue"), queues.deleted);
				assertEquals(List.of("customers-index"), indexes.deleted);
				testContext.completeNow();
			})));
	}

	@Test
	void attachedCleanupKeepsSharedDocumentIndex(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingQueueResources queues = new RecordingQueueResources();
		RecordingIndexResources indexes = new RecordingIndexResources();
		InMemoryCommandEngine commands = commandService(repository, queues, indexes);

		insertIndexer(repository, IndexResourceOwnership.ATTACHED)
			.compose(indexer -> commands.submit(new DeleteIndexerCommand(indexer.indexerId(), 0L)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of("customers-queue"), queues.deleted);
				assertTrue(indexes.deleted.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void cleanupRetryReloadsAfterFinalizationVersionConflict(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();

		insertIndexer(repository, IndexResourceOwnership.OWNER)
			.compose(indexer -> new MetadataIndexerOperations(
				repository,
				TestMetadataChangeNotifiers.create(eventBus)
			)
				.markDeleting(new MarkIndexerDeletingRequest(indexer.indexerId(), 0L)))
			.compose(marked -> {
				Integer indexerId = marked.orElseThrow().id();
				VersionChangingQueueResources queues = new VersionChangingQueueResources(
					repository,
					indexerId
				);
				CleanupDeletingIndexerCommandHandler cleanup =
					new CleanupDeletingIndexerCommandHandler(
						repository,
						queues,
						new RecordingIndexResources()
					);
				CleanupDeletingIndexerCommand command =
					new CleanupDeletingIndexerCommand(indexerId);

				return cleanup.handle(command).transform(first -> {
					assertTrue(first.failed());
					assertInstanceOf(RetryableStaleStateException.class, first.cause());
					return cleanup.handle(command);
				}).compose(ignored -> repository.getIndexerById(indexerId)
					.map(found -> new RetryResult(found, queues.deletedQueues())));
			})
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertTrue(result.indexer().isEmpty());
				assertEquals(List.of("customers-queue", "customers-queue"), result.deletedQueues());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandEngine commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerQueueResourceManager queues,
		IndexerDocumentIndexResourceManager indexes
	) {
		InMemoryCommandEngine commands = new InMemoryCommandEngine();
		return commands
			.register(new CleanupDeletingIndexerCommandHandler(repository, queues, indexes))
			.register(new DeleteIndexerCommandHandler(
				new MetadataIndexerOperations(
					repository,
					TestMetadataChangeNotifiers.create(new InMemoryIndexerLifecycleEventBus())
				),
				commands
			));
	}

	private Future<InsertedIndexer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexResourceOwnership ownership
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				ownership,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).map(indexerId -> new InsertedIndexer(targetId, indexerId)));
	}

	private static class RecordingQueueResources implements IndexerQueueResourceManager {
		private final List<String> deleted = new ArrayList<>();

		@Override
		public Future<Void> ensure(String queueName) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> delete(String queueName) {
			deleted.add(queueName);
			return Future.succeededFuture();
		}

		protected List<String> deletedQueues() {
			return List.copyOf(deleted);
		}
	}

	private static class VersionChangingQueueResources extends RecordingQueueResources {
		private final InMemoryDocumentStoreMetadataRepository repository;
		private final Integer indexerId;
		private final AtomicBoolean changeVersion = new AtomicBoolean(true);

		private VersionChangingQueueResources(
			InMemoryDocumentStoreMetadataRepository repository,
			Integer indexerId
		) {
			this.repository = repository;
			this.indexerId = indexerId;
		}

		@Override
		public Future<Void> delete(String queueName) {
			super.delete(queueName);
			if (!changeVersion.compareAndSet(true, false)) {
				return Future.succeededFuture();
			}

			return repository.getIndexerById(indexerId)
				.compose(found -> repository.updateIndexerRuntimeState(
					new UpdateIndexerRuntimeState(
						indexerId,
						IndexerRuntimeState.ACTIVE,
						found.orElseThrow().version()
					)
				));
		}
	}

	private static class RecordingIndexResources implements IndexerDocumentIndexResourceManager {
		private final List<String> deleted = new ArrayList<>();

		@Override
		public Future<Void> ensure(String indexName, IndexDefinition definition) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> delete(String indexName) {
			deleted.add(indexName);
			return Future.succeededFuture();
		}
	}

	private record InsertedIndexer(Integer targetId, Integer indexerId) {
	}

	private record CleanupResult(
		java.util.Optional<com.inqwise.indexer.metadata.IndexerRecord> indexer,
		java.util.Optional<com.inqwise.indexer.metadata.PublicationRecord> publication,
		java.util.Optional<com.inqwise.indexer.metadata.ManifestRecord> manifest
	) {
	}

	private record RetryResult(
		java.util.Optional<com.inqwise.indexer.metadata.IndexerRecord> indexer,
		List<String> deletedQueues
	) {
	}
}
