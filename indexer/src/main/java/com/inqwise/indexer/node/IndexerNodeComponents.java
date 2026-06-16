package com.inqwise.indexer.node;

import com.inqwise.indexer.IndexerRuntime;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.hot.InvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteMetadataChangeListener;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

public record IndexerNodeComponents(
	HotIndexActionsService hotIndexActionsService,
	IndexerRuntime runtime,
	DocumentStoreMetadataRepository repository,
	IndexerLifecycleEventBus lifecycleEventBus,
	IndexerQueueResourceManager queueResources,
	TargetDefinitionProvider targetDefinitionProvider,
	IndexerDefinitionProvider indexerDefinitionProvider,
	IndexerDocumentIndexResourceManager documentIndexResources,
	InvalidRouteCache invalidRouteCache,
	InvalidRouteMetadataChangeListener invalidRouteMetadataChangeListener
) {
}
