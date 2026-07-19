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

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {
			private LoadPublicationRepository publicationRepository;
			private LoadCleanupRepository cleanupRepository;
			private IndexerLoadRepository loadRepository;
			private IndexerLifecycleEventBus eventBus;

			private Builder() {
			}

			public Builder withPublicationRepository(LoadPublicationRepository value) {
				publicationRepository = value;
				return this;
			}

			public Builder withCleanupRepository(LoadCleanupRepository value) {
				cleanupRepository = value;
				return this;
			}

			public Builder withLoadRepository(IndexerLoadRepository value) {
				loadRepository = value;
				return this;
			}

			public Builder withEventBus(IndexerLifecycleEventBus value) {
				eventBus = value;
				return this;
			}

			public Config build() {
				return new Config(
					Objects.requireNonNull(publicationRepository, "publicationRepository"),
					Objects.requireNonNull(cleanupRepository, "cleanupRepository"),
					Objects.requireNonNull(loadRepository, "loadRepository"),
					eventBus
				);
			}
		}
	}
}
