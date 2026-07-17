package com.inqwise.indexer.provisioning;

import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.provisioning.definitions.IndexDefinition;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinition;
import com.inqwise.indexer.provisioning.definitions.QueueDefinition;
import com.inqwise.indexer.adapters.local.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerProvisioningServiceTest {
	@Test
	void requiresExplicitProvisioningPolicy() {
		assertEquals("role", assertThrows(
			NullPointerException.class,
			() -> provisioningRequest(null, IndexResourceOwnership.OWNER, IndexerRuntimeState.NON_ACTIVE)
		).getMessage());
		assertEquals("indexOwnership", assertThrows(
			NullPointerException.class,
			() -> provisioningRequest(IndexerRole.LIVE_WRITER, null, IndexerRuntimeState.NON_ACTIVE)
		).getMessage());
		assertEquals("runtimeState", assertThrows(
			NullPointerException.class,
			() -> provisioningRequest(IndexerRole.LIVE_WRITER, IndexResourceOwnership.OWNER, null)
		).getMessage());
	}

	@Test
	void requiresExplicitMetadataPrefix() {
		NullPointerException error = assertThrows(NullPointerException.class, () ->
			new CreateIndexerProvisioningRequest(
				null,
				1,
				"customers-index",
				"customers-queue",
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.NON_ACTIVE
			)
		);

		assertEquals("prefix", error.getMessage());
	}

	@Test
	void rejectsBlankMetadataPrefix() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
			new CreateIndexerProvisioningRequest(
				" ",
				1,
				"customers-index",
				"customers-queue",
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.NON_ACTIVE
			)
		);

		assertEquals("prefix must not be blank", error.getMessage());
	}

	@Test
	void requiresExplicitQueueIdentity() {
		NullPointerException error = assertThrows(NullPointerException.class, () ->
			new CreateIndexerProvisioningRequest(
				"missing-queue",
				1,
				"customers-index",
				null,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.NON_ACTIVE
			)
		);

		assertEquals("queueName", error.getMessage());
	}

	@Test
	void rejectsBlankQueueIdentity() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
			new CreateIndexerProvisioningRequest(
				"missing-queue",
				1,
				"customers-index",
				" ",
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.NON_ACTIVE
			)
		);

		assertEquals("queueName must not be blank", error.getMessage());
	}

	@Test
	void rejectsMissingTargetBeforeCreatingIndexer(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerProvisioningService service = new MetadataIndexerProvisioningService(
			repository,
			new StaticIndexerDefinitionProvider(new IndexerDefinition(
				new IndexDefinition("default", "1", new JsonObject(), new JsonObject()),
				new QueueDefinition(new JsonObject())
			)),
			IndexerDocumentIndexResourceManager.NOOP,
			IndexerQueueResourceManager.NOOP
		);

		service.createIndexer(new CreateIndexerProvisioningRequest(
			"missing-target",
			999,
			"customers--idx-missing",
			"customers--queue-missing",
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerRuntimeState.NON_ACTIVE
		)).onComplete(testContext.failing(error -> {
			repository.listIndexersByTargetId(999)
				.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
					assertEquals("Target not found: 999", error.getMessage());
					assertTrue(indexers.isEmpty());
					testContext.completeNow();
				})));
		}));
	}

	@Test
	void createsIndexerWithRoleAndOwnership(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		IndexerProvisioningService service = new MetadataIndexerProvisioningService(
			repository,
			new StaticIndexerDefinitionProvider(new IndexerDefinition(
				new IndexDefinition("default", "1", new JsonObject(), new JsonObject()),
				new QueueDefinition(new JsonObject())
			)),
			IndexerDocumentIndexResourceManager.NOOP,
			IndexerQueueResourceManager.NOOP
		);

		repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> service.createIndexer(new CreateIndexerProvisioningRequest(
				"load-writer",
				targetId,
				"customers--idx-load",
				"customers--queue-load",
				IndexerRole.LOAD_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE
			)).compose(indexer -> TestMetadataChangeNotifiers.create(eventBus).indexerChanged(
				new com.inqwise.indexer.lifecycle.IndexerMetadataChanged(
					indexer.indexerId(), indexer.targetId(), "indexer.create", indexer.version()
				)
			)).compose(ignored -> repository.listIndexersByTargetId(targetId)))
			.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
				assertEquals(1, indexers.size());
				assertEquals("customers", indexers.get(0).targetName());
				assertEquals(IndexerType.INDEX, indexers.get(0).type());
				assertEquals(IndexerRole.LOAD_WRITER, indexers.get(0).role());
				assertEquals(IndexResourceOwnership.OWNER, indexers.get(0).indexOwnership());
				assertEquals(IndexerRuntimeState.ACTIVE, indexers.get(0).runtimeState());
				assertEquals(MutationState.WRITABLE, indexers.get(0).mutationState());
				assertEquals(PublicationState.UNPUBLISHED, indexers.get(0).publicationState());
				assertTrue(eventBus.events().stream()
					.anyMatch(event -> event.getIndexerId().equals(indexers.get(0).id())
						&& "indexer.create".equals(event.getCommandType())));
				testContext.completeNow();
			})));
	}

	private CreateIndexerProvisioningRequest provisioningRequest(
		IndexerRole role,
		IndexResourceOwnership indexOwnership,
		IndexerRuntimeState runtimeState
	) {
		return new CreateIndexerProvisioningRequest(
			"indexer-customers",
			1,
			"customers-index",
			"customers-queue",
			role,
			indexOwnership,
			runtimeState
		);
	}
}
