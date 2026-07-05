package com.inqwise.indexer.commands;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.MetadataChangeNotifier;
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
			new CleanupDeletingIndexerCommandHandler(
				config.repository(),
				config.queueResources(),
				config.documentIndexResources()
			),
			new CleanupResetIndexerQueueCommandHandler(config.queueResources()),
			new DeleteIndexerCommandHandler(config.indexerOperations(), commandService)
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
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerOperations indexerOperations
	) {
		public Config {
			Objects.requireNonNull(repository, "repository");
			Objects.requireNonNull(documentIndexResources, "documentIndexResources");
			Objects.requireNonNull(queueResources, "queueResources");
			Objects.requireNonNull(metadataChangeNotifier, "metadataChangeNotifier");
			Objects.requireNonNull(indexerOperations, "indexerOperations");
		}
	}
}
