package com.inqwise.indexer.node;

import java.util.Objects;

import com.inqwise.indexer.commands.CommandEngine;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.runtime.IndexerQueueClient;

public record IndexerPluginContext(
	DocumentStoreMetadataRepository repository,
	IndexerQueueClient queue,
	CommandEngine commandEngine,
	IndexerLifecycleEventBus lifecycleEventBus
) {
	public IndexerPluginContext {
		Objects.requireNonNull(repository, "repository");
		Objects.requireNonNull(queue, "queue");
		Objects.requireNonNull(commandEngine, "commandEngine");
		Objects.requireNonNull(lifecycleEventBus, "lifecycleEventBus");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private DocumentStoreMetadataRepository repository;
		private IndexerQueueClient queue;
		private CommandEngine commandEngine;
		private IndexerLifecycleEventBus lifecycleEventBus;

		private Builder() {
		}

		public Builder withRepository(DocumentStoreMetadataRepository value) {
			repository = value;
			return this;
		}

		public Builder withQueue(IndexerQueueClient value) {
			queue = value;
			return this;
		}

		public Builder withCommandEngine(CommandEngine value) {
			commandEngine = value;
			return this;
		}

		public Builder withLifecycleEventBus(IndexerLifecycleEventBus value) {
			lifecycleEventBus = value;
			return this;
		}

		public IndexerPluginContext build() {
			return new IndexerPluginContext(
				repository,
				queue,
				commandEngine,
				lifecycleEventBus
			);
		}
	}
}
