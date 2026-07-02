package com.inqwise.indexer.node;

import com.inqwise.indexer.IndexerRuntime;
import com.inqwise.indexer.IndexerRuntimeReconciler;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.commands.CommandEngine;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.hot.InvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationPoller;
import com.inqwise.indexer.hot.TargetInvalidationRegistry;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.operations.IndexerOperations;
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
}
