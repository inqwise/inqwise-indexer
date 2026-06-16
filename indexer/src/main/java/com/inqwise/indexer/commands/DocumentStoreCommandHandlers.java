package com.inqwise.indexer.commands;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

public final class DocumentStoreCommandHandlers {
	private DocumentStoreCommandHandlers() {
	}

	public static List<CommandHandler> create(Config config) {
		Objects.requireNonNull(config, "config");

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
			new DeleteIndexerCommandHandler(config.repository(), config.eventBus()),
			new ResetIndexerQueueCommandHandler(
				config.repository(),
				config.eventBus(),
				config.queueResources()
			)
		);
	}

	public static InMemoryCommandService register(
		InMemoryCommandService commandService,
		Config config
	) {
		Objects.requireNonNull(commandService, "commandService");
		create(config).forEach(commandService::register);
		return commandService;
	}

	public record Config(
		DocumentStoreMetadataRepository repository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources,
		IndexerLifecycleEventBus eventBus
	) {
		public Config {
			Objects.requireNonNull(repository, "repository");
			Objects.requireNonNull(targetDefinitionProvider, "targetDefinitionProvider");
			Objects.requireNonNull(indexerDefinitionProvider, "indexerDefinitionProvider");
			Objects.requireNonNull(documentIndexResources, "documentIndexResources");
			Objects.requireNonNull(queueResources, "queueResources");
			eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		}
	}
}
