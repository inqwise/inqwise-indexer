package com.inqwise.indexer.provisioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.definitions.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerProvisioningServiceTest {
	@Test
	void createsReadyIndexerWithManifestAndPublication(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingDocumentIndexResourceManager documentResources =
			new RecordingDocumentIndexResourceManager();
		RecordingQueueResourceManager queueResources = new RecordingQueueResourceManager();
		IndexerProvisioningService service = service(
			repository,
			documentResources,
			queueResources
		);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> service.createIndexer(request(targetId)))
			.compose(indexer -> repository.getActiveManifestByIndexerId(indexer.id())
				.compose(manifest -> repository.getPublicationByIndexerId(indexer.id())
					.map(publication -> new Result(indexer, manifest.isPresent(), publication.isPresent()))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(IndexerProvisioningState.READY, result.indexer().provisioningState());
				assertEquals(1L, result.indexer().version());
				assertTrue(result.manifestCreated());
				assertTrue(result.publicationCreated());
				assertEquals(List.of("customers-index"), documentResources.ensured);
				assertEquals(List.of("customers-queue"), queueResources.ensured);
				testContext.completeNow();
			})));
	}

	@Test
	void marksIndexerFailedWhenResourceProvisioningFails(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingDocumentIndexResourceManager documentResources =
			new RecordingDocumentIndexResourceManager();
		documentResources.failure = new IllegalStateException("index create failed");
		RecordingQueueResourceManager queueResources = new RecordingQueueResourceManager();
		IndexerProvisioningService service = service(
			repository,
			documentResources,
			queueResources
		);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> service.createIndexer(request(targetId))
				.recover(error -> repository.listIndexersByTargetId(targetId)
					.compose(indexers -> {
						assertEquals("index create failed", error.getMessage());
						assertEquals(1, indexers.size());
						return Future.failedFuture(error);
					})))
			.onComplete(testContext.failing(error -> repository.listIndexersByTargetId(1)
				.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
					assertEquals(IndexerProvisioningState.FAILED, indexers.get(0).provisioningState());
					assertEquals(1L, indexers.get(0).version());
					assertTrue(queueResources.ensured.isEmpty());
					testContext.completeNow();
				})))));
	}

	private IndexerProvisioningService service(
		InMemoryDocumentStoreMetadataRepository repository,
		RecordingDocumentIndexResourceManager documentResources,
		RecordingQueueResourceManager queueResources
	) {
		return new IndexerProvisioningService(
			repository,
			new StaticIndexerDefinitionProvider(new IndexerDefinition(
				new IndexDefinition(
					"customers",
					"v1",
					new JsonObject().put("number_of_shards", 1),
					new JsonObject().put("properties", new JsonObject())
				),
				new QueueDefinition(new JsonObject().put("partitions", 3))
			)),
			documentResources,
			queueResources
		);
	}

	private CreateIndexerProvisioningRequest request(Integer targetId) {
		return new CreateIndexerProvisioningRequest(
			"indexer-customers",
			targetId,
			"customers",
			"customers-index",
			"customers-queue",
			IndexerType.INDEX,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerRuntimeState.NON_ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		);
	}

	private record Result(
		com.inqwise.indexer.metadata.IndexerRecord indexer,
		boolean manifestCreated,
		boolean publicationCreated
	) {
	}

	private static class RecordingDocumentIndexResourceManager
		implements IndexerDocumentIndexResourceManager {
		private final List<String> ensured = new ArrayList<>();
		private Throwable failure;

		@Override
		public Future<Void> ensure(String indexName, IndexDefinition definition) {
			ensured.add(indexName);
			return failure == null ? Future.succeededFuture() : Future.failedFuture(failure);
		}

		@Override
		public Future<Void> delete(String indexName) {
			return Future.succeededFuture();
		}
	}

	private static class RecordingQueueResourceManager implements IndexerQueueResourceManager {
		private final List<String> ensured = new ArrayList<>();

		@Override
		public Future<Void> ensure(String queueName) {
			ensured.add(queueName);
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> delete(String queueName) {
			return Future.succeededFuture();
		}
	}
}
