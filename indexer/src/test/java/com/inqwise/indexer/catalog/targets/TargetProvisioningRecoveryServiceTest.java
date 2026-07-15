package com.inqwise.indexer.catalog.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.adapters.local.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.adapters.local.StaticTargetDefinitionProvider;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;
import com.inqwise.indexer.metadata.UpdateTargetStatus;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;
import com.inqwise.indexer.provisioning.IndexerProvisioningService;
import com.inqwise.indexer.provisioning.MetadataIndexerProvisioningService;
import com.inqwise.indexer.publication.MetadataIndexPublicationService;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TargetProvisioningRecoveryServiceTest {
	@Test
	void recoversFailedActiveTargetToReady(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		TargetManagementService service = targetManagementService(repository, eventBus);

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.FAILED
		)).compose(targetId -> service.recoverProvisioning(new RecoverTargetProvisioningRequest(
			targetId,
			0L
		)).compose(ignored -> repository.getTargetById(targetId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(TargetProvisioningState.READY, found.orElseThrow().provisioningState());
				assertEquals(1L, found.orElseThrow().version());
				assertEquals(1, eventBus.targetEvents().size());
				assertEquals(found.orElseThrow().id(), eventBus.targetEvents().get(0).getTargetId());
				testContext.completeNow();
			})));
	}

	@Test
	void failsWhenTargetIsNotFailed(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		TargetManagementService service = targetManagementService(repository);

		repository.insertTarget(new InsertTarget("target-customers", "customers", null))
			.compose(targetId -> service.recoverProvisioning(new RecoverTargetProvisioningRequest(
				targetId,
				0L
			))).onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("Target provisioning is not failed: 1", error.getMessage());
				testContext.completeNow();
			})));
	}

	@Test
	void exactRecoveryRedeliveryDoesNotAdvanceVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		TargetManagementService service = targetManagementService(repository, eventBus);

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.FAILED
		)).compose(targetId -> {
			RecoverTargetProvisioningRequest recover =
				new RecoverTargetProvisioningRequest(targetId, 0L);
			return service.recoverProvisioning(recover)
				.compose(ignored -> service.recoverProvisioning(recover))
				.compose(ignored -> repository.getTargetById(targetId));
		}).onComplete(testContext.succeeding(found -> testContext.verify(() -> {
			assertEquals(TargetProvisioningState.READY, found.orElseThrow().provisioningState());
			assertEquals(1L, found.orElseThrow().version());
			assertEquals(2, eventBus.targetEvents().size());
			testContext.completeNow();
		})));
	}

	@Test
	void recoveryRedeliveryRejectsLaterReadyVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		TargetManagementService service = targetManagementService(repository);

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.FAILED
		)).compose(targetId -> service.recoverProvisioning(new RecoverTargetProvisioningRequest(
			targetId,
			0L
		)).compose(ignored -> repository.updateTargetProvisioningState(
			new UpdateTargetProvisioningState(
				targetId,
				TargetProvisioningState.PROVISIONING,
				1L
			)
		)).compose(ignored -> repository.updateTargetProvisioningState(
			new UpdateTargetProvisioningState(
				targetId,
				TargetProvisioningState.READY,
				2L
			)
		)).compose(ignored -> service.recoverProvisioning(new RecoverTargetProvisioningRequest(
			targetId,
			0L
		)))).onComplete(testContext.failing(error -> testContext.verify(() -> {
			assertEquals(
				"Target version conflict for id 1: expected 0 but was 3",
				error.getMessage()
			);
			testContext.completeNow();
		})));
	}

	@Test
	void failsWhenTargetIsNotActive(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		TargetManagementService service = targetManagementService(repository);

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.FAILED
		)).compose(targetId -> repository.updateTargetStatus(new UpdateTargetStatus(
			targetId,
			TargetStatus.NON_ACTIVE,
			0L
		)).compose(ignored -> service.recoverProvisioning(new RecoverTargetProvisioningRequest(
			targetId,
			1L
		)))).onComplete(testContext.failing(error -> testContext.verify(() -> {
			assertEquals("Target is not active: 1", error.getMessage());
			testContext.completeNow();
		})));
	}

	@Test
	void failsOnExpectedVersionConflict(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		TargetManagementService service = targetManagementService(repository);

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.FAILED
		)).compose(targetId -> repository.updateTargetProvisioningState(
			new UpdateTargetProvisioningState(
				targetId,
				TargetProvisioningState.PROVISIONING,
				0L
			)
		).compose(ignored -> repository.updateTargetProvisioningState(
			new UpdateTargetProvisioningState(
				targetId,
				TargetProvisioningState.FAILED,
				1L
			)
		)).compose(ignored -> service.recoverProvisioning(new RecoverTargetProvisioningRequest(
			targetId,
			0L
		)))).onComplete(testContext.failing(error -> testContext.verify(() -> {
			assertEquals(
				"Target version conflict for id 1: expected 0 but was 2",
				error.getMessage()
			);
			testContext.completeNow();
		})));
	}

	private TargetManagementService targetManagementService(
		InMemoryDocumentStoreMetadataRepository repository
	) {
		return targetManagementService(repository, new InMemoryIndexerLifecycleEventBus());
	}

	private TargetManagementService targetManagementService(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		StaticIndexerDefinitionProvider indexerDefinitions =
			new StaticIndexerDefinitionProvider(new IndexerDefinition(
				new IndexDefinition("customers", "v1", new JsonObject(), new JsonObject()),
				new QueueDefinition(new JsonObject())
			));
		return new MetadataTargetManagementService(
			repository,
			new StaticTargetDefinitionProvider(java.util.List.of(
				new TargetDefinition("customers", TargetPeriodStrategy.MONTHLY)
			)),
			new MetadataIndexerProvisioningService(
				repository,
				indexerDefinitions,
				IndexerDocumentIndexResourceManager.NOOP,
				IndexerQueueResourceManager.NOOP
			),
			new MetadataIndexPublicationService(
				repository,
				indexerDefinitions,
				IndexerDocumentIndexResourceManager.NOOP,
				IndexerQueueResourceManager.NOOP
			),
			TestMetadataChangeNotifiers.create(eventBus)
		);
	}
}
