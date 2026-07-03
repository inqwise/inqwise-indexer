package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.TestMetadataChangeNotifiers;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;
import com.inqwise.indexer.metadata.UpdateTargetStatus;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class RecoverTargetProvisioningCommandTest {
	@Test
	void recoversFailedActiveTargetToReady(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandEngine commands = commandService(repository, eventBus);

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.FAILED
		)).compose(targetId -> commands.submit(new RecoverTargetProvisioningCommand(
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
		InMemoryCommandEngine commands = commandService(repository);

		repository.insertTarget(new InsertTarget("target-customers", "customers", null))
			.compose(targetId -> commands.submit(new RecoverTargetProvisioningCommand(
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
		InMemoryCommandEngine commands = commandService(repository, eventBus);

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.FAILED
		)).compose(targetId -> {
			RecoverTargetProvisioningCommand recover =
				new RecoverTargetProvisioningCommand(targetId, 0L);
			return commands.submit(recover)
				.compose(ignored -> commands.submit(recover))
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
		InMemoryCommandEngine commands = commandService(repository);

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.FAILED
		)).compose(targetId -> commands.submit(new RecoverTargetProvisioningCommand(
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
		)).compose(ignored -> commands.submit(new RecoverTargetProvisioningCommand(
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
		InMemoryCommandEngine commands = commandService(repository);

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
		)).compose(ignored -> commands.submit(new RecoverTargetProvisioningCommand(
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
		InMemoryCommandEngine commands = commandService(repository);

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
		)).compose(ignored -> commands.submit(new RecoverTargetProvisioningCommand(
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

	private InMemoryCommandEngine commandService(
		InMemoryDocumentStoreMetadataRepository repository
	) {
		return commandService(repository, new InMemoryIndexerLifecycleEventBus());
	}

	private InMemoryCommandEngine commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		return new InMemoryCommandEngine()
			.register(new RecoverTargetProvisioningCommandHandler(
				repository,
				TestMetadataChangeNotifiers.create(eventBus)
			));
	}
}
