package com.inqwise.indexer.commands;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.operations.IndexerOperations;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

public final class DocumentStoreCommandHandlers {
	private DocumentStoreCommandHandlers() {
	}

	public static List<CommandHandler> create(
		Config config,
		CommandService commandService
	) {
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(commandService, "commandService");

		return List.of(
			new CreateTargetCommandHandler(
				config.repository(),
				config.targetDefinitionProvider(),
				config.indexerDefinitionProvider(),
				config.documentIndexResources(),
				config.queueResources(),
				config.eventBus()
			),
			new CreateIndexerCommandHandler(
				config.repository(),
				config.indexerDefinitionProvider(),
				config.documentIndexResources(),
				config.queueResources(),
				config.eventBus()
			),
			new MarkIndexReadyCommandHandler(config.repository()),
			new PublishIndexCommandHandler(config.repository()),
			new RetireIndexCommandHandler(config.repository()),
			new RecoverTargetProvisioningCommandHandler(config.repository(), config.eventBus()),
			new ActivateIndexerCommandHandler(config.repository(), config.eventBus()),
			new DeactivateIndexerCommandHandler(config.repository(), config.eventBus()),
			new CleanupDeletingIndexerCommandHandler(
				config.repository(),
				config.queueResources(),
				config.documentIndexResources()
			),
			new DeleteIndexerCommandHandler(config.indexerOperations(), commandService),
			new ResetIndexerQueueCommandHandler(
				config.repository(),
				config.eventBus(),
				config.queueResources()
			)
		);
	}

	public static <T extends CommandEngine> T register(
		T commandEngine,
		Config config
	) {
		Objects.requireNonNull(commandEngine, "commandEngine");
		create(config, commandEngine).forEach(commandEngine::register);
		return commandEngine;
	}

	public record Config(
		DocumentStoreMetadataRepository repository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources,
		IndexerLifecycleEventBus eventBus,
		IndexerOperations indexerOperations
	) {
		public Config {
			Objects.requireNonNull(repository, "repository");
			Objects.requireNonNull(targetDefinitionProvider, "targetDefinitionProvider");
			Objects.requireNonNull(indexerDefinitionProvider, "indexerDefinitionProvider");
			Objects.requireNonNull(documentIndexResources, "documentIndexResources");
			Objects.requireNonNull(queueResources, "queueResources");
			eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
			Objects.requireNonNull(indexerOperations, "indexerOperations");
		}
	}
}
