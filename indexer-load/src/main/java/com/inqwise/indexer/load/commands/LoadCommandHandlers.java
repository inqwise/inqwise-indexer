package com.inqwise.indexer.load.commands;

import com.inqwise.indexer.load.repository.IndexerLoadRepository;
import com.inqwise.indexer.load.repository.LoadCleanupRepository;
import com.inqwise.indexer.load.repository.LoadPublicationRepository;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.commands.CommandEngine;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;

public final class LoadCommandHandlers {
	private LoadCommandHandlers() {
	}

	public static List<CommandHandler> create(Config config, CommandService commandService) {
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(commandService, "commandService");

		return List.of(
			new PublishLoadCommandHandler(
				config.publicationRepository(),
				config.loadRepository(),
				config.eventBus(),
				commandService
			),
			new CleanupLoadCommandHandler(
				config.cleanupRepository(),
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
		LoadPublicationRepository publicationRepository,
		LoadCleanupRepository cleanupRepository,
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus
	) {
		public Config {
			Objects.requireNonNull(publicationRepository, "publicationRepository");
			Objects.requireNonNull(cleanupRepository, "cleanupRepository");
			Objects.requireNonNull(loadRepository, "loadRepository");
			eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		}
	}
}
