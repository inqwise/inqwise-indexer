package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.definitions.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.definitions.StaticTargetDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinition;
import com.inqwise.indexer.metadata.ConcreteTargetKey;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.operations.IndexerOperations;
import com.inqwise.indexer.operations.MetadataIndexerOperations;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DocumentStoreCommandHandlersTest {
	@Test
	void createsStandardLifecycleAndProvisioningHandlers() {
		InMemoryCommandEngine commandService = new InMemoryCommandEngine();
		List<CommandHandler> handlers = DocumentStoreCommandHandlers.create(
			config(new InMemoryDocumentStoreMetadataRepository()),
			commandService
		);

		Set<String> types = handlers.stream()
			.map(CommandHandler::getType)
			.collect(Collectors.toSet());

		assertEquals(12, handlers.size());
		assertTrue(types.contains(CreateTargetCommand.TYPE));
		assertTrue(types.contains(CreateIndexerCommand.TYPE));
		assertTrue(types.contains(MarkIndexReadyCommand.TYPE));
		assertTrue(types.contains(PublishIndexCommand.TYPE));
		assertTrue(types.contains(RetireIndexCommand.TYPE));
		assertTrue(types.contains(RecoverTargetProvisioningCommand.TYPE));
		assertTrue(types.contains(ActivateIndexerCommand.TYPE));
		assertTrue(types.contains(DeactivateIndexerCommand.TYPE));
		assertTrue(types.contains(DeleteIndexerCommand.TYPE));
		assertTrue(types.contains(CleanupDeletingIndexerCommand.TYPE));
		assertTrue(types.contains(CleanupResetIndexerQueueCommand.TYPE));
		assertTrue(types.contains(ResetIndexerQueueCommand.TYPE));
	}

	@Test
	void registersTargetCreationHandlerWithCommandService(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryCommandEngine commandService = DocumentStoreCommandHandlers.register(
			new InMemoryCommandEngine(),
			config(repository)
		);

		commandService.submit(new CreateTargetCommand(
			"target-customers",
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			new CreateTargetCommand.CreateIndexer(
				"indexer-customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRole.LIVE_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.NON_ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE,
				InitialPublicationMode.PUBLISH
			)
		)).compose(ignored -> repository.getTargetByDefinitionAndPeriod(new ConcreteTargetKey(
			"customers",
			"2026-05"
		))).onComplete(testContext.succeeding(found -> testContext.verify(() -> {
			assertTrue(found.isPresent());
			assertEquals(TargetProvisioningState.READY, found.get().provisioningState());
			testContext.completeNow();
		})));
	}

	private DocumentStoreCommandHandlers.Config config(
		InMemoryDocumentStoreMetadataRepository repository
	) {
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		return new DocumentStoreCommandHandlers.Config(
			repository,
			new StaticTargetDefinitionProvider(List.of(
				new TargetDefinition("customers", TargetPeriodStrategy.MONTHLY)
			)),
			new StaticIndexerDefinitionProvider(new IndexerDefinition(
				new IndexDefinition("customers", "v1", new JsonObject(), new JsonObject()),
				new QueueDefinition(new JsonObject())
			)),
			IndexerDocumentIndexResourceManager.NOOP,
			IndexerQueueResourceManager.NOOP,
			eventBus,
			new MetadataIndexerOperations(repository, eventBus)
		);
	}
}
