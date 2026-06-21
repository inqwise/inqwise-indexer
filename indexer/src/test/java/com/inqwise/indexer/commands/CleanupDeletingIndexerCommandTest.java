package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertManifest;
import com.inqwise.indexer.metadata.InsertPublication;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.ManifestStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.ReadinessState;
import com.inqwise.indexer.metadata.UpdateIndexerQueueName;
import com.inqwise.indexer.operations.IndexerOperations;
import com.inqwise.indexer.operations.MarkIndexerDeletingRequest;
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
		InMemoryCommandService commands = commandService(repository, queues, indexes);

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
		InMemoryCommandService commands = commandService(repository, queues, indexes);

		insertIndexer(repository, IndexResourceOwnership.ATTACHED)
			.compose(indexer -> commands.submit(new DeleteIndexerCommand(indexer.indexerId(), 0L)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of("customers-queue"), queues.deleted);
				assertTrue(indexes.deleted.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void cleanupRejectsUnexpectedDeletingVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueueResources queues = new RecordingQueueResources();
		CleanupDeletingIndexerCommandHandler cleanup = new CleanupDeletingIndexerCommandHandler(
			repository,
			queues,
			new RecordingIndexResources()
		);

		insertIndexer(repository, IndexResourceOwnership.OWNER)
			.compose(indexer -> new IndexerOperations(repository, eventBus)
				.markDeleting(new MarkIndexerDeletingRequest(indexer.indexerId(), 0L)))
			.compose(marked -> repository.updateIndexerQueueName(new UpdateIndexerQueueName(
				marked.orElseThrow().id(),
				"changed-queue",
				marked.orElseThrow().version()
			)).compose(ignored -> cleanup.handle(new CleanupDeletingIndexerCommand(
				marked.orElseThrow().id(),
				marked.orElseThrow().version()
			))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertInstanceOf(RetryableStaleStateException.class, error);
				assertTrue(queues.deleted.isEmpty());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerQueueResourceManager queues,
		IndexerDocumentIndexResourceManager indexes
	) {
		InMemoryCommandService commands = new InMemoryCommandService();
		return commands
			.register(new CleanupDeletingIndexerCommandHandler(repository, queues, indexes))
			.register(new DeleteIndexerCommandHandler(
				new IndexerOperations(repository, new InMemoryIndexerLifecycleEventBus()),
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
}
