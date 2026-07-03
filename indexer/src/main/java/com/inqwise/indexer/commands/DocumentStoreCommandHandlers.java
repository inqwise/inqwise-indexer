package com.inqwise.indexer.commands;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.MetadataChangeNotifier;
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
				config.metadataChangeNotifier()
			),
			new CreateIndexerCommandHandler(
				config.repository(),
				config.indexerDefinitionProvider(),
				config.documentIndexResources(),
				config.queueResources(),
				config.metadataChangeNotifier()
			),
			new MarkIndexReadyCommandHandler(config.repository()),
			new PublishIndexCommandHandler(
				config.repository(),
				config.indexerDefinitionProvider(),
				config.documentIndexResources(),
				config.queueResources()
			),
			new RetireIndexCommandHandler(config.repository()),
			new RecoverTargetProvisioningCommandHandler(
				config.repository(),
				config.metadataChangeNotifier()
			),
			new ActivateIndexerCommandHandler(config.repository(), config.metadataChangeNotifier()),
			new DeactivateIndexerCommandHandler(config.repository(), config.metadataChangeNotifier()),
			new CleanupDeletingIndexerCommandHandler(
				config.repository(),
				config.queueResources(),
				config.documentIndexResources()
			),
			new CleanupResetIndexerQueueCommandHandler(config.queueResources()),
			new DeleteIndexerCommandHandler(config.indexerOperations(), commandService),
			new ResetIndexerQueueCommandHandler(
				config.repository(),
				config.metadataChangeNotifier(),
				config.queueResources(),
				commandService
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
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerOperations indexerOperations
	) {
		public Config {
			Objects.requireNonNull(repository, "repository");
			Objects.requireNonNull(targetDefinitionProvider, "targetDefinitionProvider");
			Objects.requireNonNull(indexerDefinitionProvider, "indexerDefinitionProvider");
			Objects.requireNonNull(documentIndexResources, "documentIndexResources");
			Objects.requireNonNull(queueResources, "queueResources");
			Objects.requireNonNull(metadataChangeNotifier, "metadataChangeNotifier");
			Objects.requireNonNull(indexerOperations, "indexerOperations");
		}
	}
}
