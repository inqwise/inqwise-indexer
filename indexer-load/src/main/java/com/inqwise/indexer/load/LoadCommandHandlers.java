package com.inqwise.indexer.load;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.commands.CommandEngine;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;

public final class LoadCommandHandlers {
	private LoadCommandHandlers() {
	}

	public static List<CommandHandler> create(Config config, CommandService commandService) {
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(commandService, "commandService");

		return List.of(
			new StartLoadCommandHandler(
				config.metadataRepository(),
				config.loadRepository(),
				config.queueClient(),
				config.loadProviderRegistry(),
				config.eventBus()
			),
			new PublishLoadCommandHandler(
				config.metadataRepository(),
				config.loadRepository(),
				config.eventBus(),
				commandService
			),
			new CleanupLoadCommandHandler(
				config.metadataRepository(),
				config.loadRepository(),
				commandService
			)
		);
	}

	public static <T extends CommandEngine> T register(T engine, Config config) {
		Objects.requireNonNull(engine, "engine");
		create(config, engine).forEach(engine::register);
		return engine;
	}

	public record Config(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		IndexerQueueClient queueClient,
		LoadProviderRegistry loadProviderRegistry,
		IndexerLifecycleEventBus eventBus
	) {
		public Config {
			Objects.requireNonNull(metadataRepository, "metadataRepository");
			Objects.requireNonNull(loadRepository, "loadRepository");
			Objects.requireNonNull(queueClient, "queueClient");
			Objects.requireNonNull(loadProviderRegistry, "loadProviderRegistry");
			eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		}
	}
}
