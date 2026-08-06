package com.inqwise.indexer.node;

import java.util.Objects;

import com.inqwise.indexer.runtime.IndexerRuntime;
import com.inqwise.indexer.runtime.IndexerRuntimeReconciler;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.commands.CommandEngine;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.catalog.targets.TargetDefinitionProvider;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.routing.InvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationPoller;
import com.inqwise.indexer.lifecycle.TargetInvalidationRegistry;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

public record IndexerNodeComponents(
	HotIndexActionsService hotIndexActionsService,
	IndexerRuntime runtime,
	IndexerRuntimeReconciler runtimeReconciler,
	CommandEngine commandEngine,
	IndexerOperations indexerOperations,
	DocumentStoreMetadataRepository repository,
	IndexerLifecycleEventBus lifecycleEventBus,
	IndexerQueueResourceManager queueResources,
	TargetDefinitionProvider targetDefinitionProvider,
	IndexerDefinitionProvider indexerDefinitionProvider,
	IndexerDocumentIndexResourceManager documentIndexResources,
	InvalidRouteCache invalidRouteCache,
	InvalidRouteMetadataChangeListener invalidRouteMetadataChangeListener,
	TargetInvalidationRegistry targetInvalidationRegistryBackend,
	TargetInvalidationRegistry targetInvalidationRegistry,
	TargetInvalidationMetadataChangeListener targetInvalidationMetadataChangeListener,
	TargetInvalidationPoller targetInvalidationPoller
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private HotIndexActionsService hotIndexActionsService;
		private IndexerRuntime runtime;
		private IndexerRuntimeReconciler runtimeReconciler;
		private CommandEngine commandEngine;
		private IndexerOperations indexerOperations;
		private DocumentStoreMetadataRepository repository;
		private IndexerLifecycleEventBus lifecycleEventBus;
		private IndexerQueueResourceManager queueResources;
		private TargetDefinitionProvider targetDefinitionProvider;
		private IndexerDefinitionProvider indexerDefinitionProvider;
		private IndexerDocumentIndexResourceManager documentIndexResources;
		private InvalidRouteCache invalidRouteCache;
		private InvalidRouteMetadataChangeListener invalidRouteMetadataChangeListener;
		private TargetInvalidationRegistry targetInvalidationRegistryBackend;
		private TargetInvalidationRegistry targetInvalidationRegistry;
		private TargetInvalidationMetadataChangeListener targetInvalidationMetadataChangeListener;
		private TargetInvalidationPoller targetInvalidationPoller;

		private Builder() {
		}

		public Builder withHotIndexActionsService(HotIndexActionsService value) {
			hotIndexActionsService = value;
			return this;
		}

		public Builder withRuntime(IndexerRuntime value) {
			runtime = value;
			return this;
		}

		public Builder withRuntimeReconciler(IndexerRuntimeReconciler value) {
			runtimeReconciler = value;
			return this;
		}

		public Builder withCommandEngine(CommandEngine value) {
			commandEngine = value;
			return this;
		}

		public Builder withIndexerOperations(IndexerOperations value) {
			indexerOperations = value;
			return this;
		}

		public Builder withRepository(DocumentStoreMetadataRepository value) {
			repository = value;
			return this;
		}

		public Builder withLifecycleEventBus(IndexerLifecycleEventBus value) {
			lifecycleEventBus = value;
			return this;
		}

		public Builder withQueueResources(IndexerQueueResourceManager value) {
			queueResources = value;
			return this;
		}

		public Builder withTargetDefinitionProvider(TargetDefinitionProvider value) {
			targetDefinitionProvider = value;
			return this;
		}

		public Builder withIndexerDefinitionProvider(IndexerDefinitionProvider value) {
			indexerDefinitionProvider = value;
			return this;
		}

		public Builder withDocumentIndexResources(
			IndexerDocumentIndexResourceManager value
		) {
			documentIndexResources = value;
			return this;
		}

		public Builder withInvalidRouteCache(InvalidRouteCache value) {
			invalidRouteCache = value;
			return this;
		}

		public Builder withInvalidRouteMetadataChangeListener(
			InvalidRouteMetadataChangeListener value
		) {
			invalidRouteMetadataChangeListener = value;
			return this;
		}

		public Builder withTargetInvalidationRegistryBackend(
			TargetInvalidationRegistry value
		) {
			targetInvalidationRegistryBackend = value;
			return this;
		}

		public Builder withTargetInvalidationRegistry(TargetInvalidationRegistry value) {
			targetInvalidationRegistry = value;
			return this;
		}

		public Builder withTargetInvalidationMetadataChangeListener(
			TargetInvalidationMetadataChangeListener value
		) {
			targetInvalidationMetadataChangeListener = value;
			return this;
		}

		public Builder withTargetInvalidationPoller(TargetInvalidationPoller value) {
			targetInvalidationPoller = value;
			return this;
		}

		public IndexerNodeComponents build() {
			return new IndexerNodeComponents(
				Objects.requireNonNull(hotIndexActionsService, "hotIndexActionsService"),
				Objects.requireNonNull(runtime, "runtime"),
				Objects.requireNonNull(runtimeReconciler, "runtimeReconciler"),
				Objects.requireNonNull(commandEngine, "commandEngine"),
				Objects.requireNonNull(indexerOperations, "indexerOperations"),
				Objects.requireNonNull(repository, "repository"),
				Objects.requireNonNull(lifecycleEventBus, "lifecycleEventBus"),
				Objects.requireNonNull(queueResources, "queueResources"),
				Objects.requireNonNull(targetDefinitionProvider, "targetDefinitionProvider"),
				Objects.requireNonNull(indexerDefinitionProvider, "indexerDefinitionProvider"),
				Objects.requireNonNull(documentIndexResources, "documentIndexResources"),
				Objects.requireNonNull(invalidRouteCache, "invalidRouteCache"),
				Objects.requireNonNull(
					invalidRouteMetadataChangeListener,
					"invalidRouteMetadataChangeListener"
				),
				Objects.requireNonNull(
					targetInvalidationRegistryBackend,
					"targetInvalidationRegistryBackend"
				),
				Objects.requireNonNull(
					targetInvalidationRegistry,
					"targetInvalidationRegistry"
				),
				Objects.requireNonNull(
					targetInvalidationMetadataChangeListener,
					"targetInvalidationMetadataChangeListener"
				),
				Objects.requireNonNull(targetInvalidationPoller, "targetInvalidationPoller")
			);
		}
	}
}
