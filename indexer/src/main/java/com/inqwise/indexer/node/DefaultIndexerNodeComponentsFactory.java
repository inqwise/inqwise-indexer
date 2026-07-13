package com.inqwise.indexer.node;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.runtime.IndexerOptions;
import com.inqwise.indexer.runtime.IndexerRuntime;
import com.inqwise.indexer.runtime.IndexerRuntimeReconciler;
import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.lifecycle.VertxIndexerLifecycleEventBusProvider;
import com.inqwise.indexer.cleanup.DocumentStoreCommandHandlers;
import com.inqwise.indexer.commands.InMemoryCommandEngine;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.adapters.local.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.adapters.local.StaticTargetDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinition;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.hot.DefaultHotMetadataView;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.hot.HotMetadataView;
import com.inqwise.indexer.adapters.local.InMemoryInvalidRouteCache;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistryProvider;
import com.inqwise.indexer.hot.InvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationPoller;
import com.inqwise.indexer.hot.TargetInvalidationRegistry;
import com.inqwise.indexer.hot.TargetInvalidationRegistryConfig;
import com.inqwise.indexer.hot.TargetInvalidationRegistryOptions;
import com.inqwise.indexer.hot.VertxSharedDataTargetInvalidationRegistryProvider;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerOperations;
import com.inqwise.indexer.providers.IndexerProviders;
import com.inqwise.indexer.providers.MetadataIndexerProvider;
import com.inqwise.indexer.routing.RoutedIndexActionPublisher;
import com.inqwise.indexer.routing.SubmitIndexActionsCommandHandler;
import com.inqwise.indexer.service.invalidation.TargetInvalidationRegistryServices;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public final class DefaultIndexerNodeComponentsFactory {
	public IndexerNodeComponents create(
		Vertx vertx,
		IndexerNodeOptions nodeOptions
	) {
		Objects.requireNonNull(vertx, "vertx");
		Objects.requireNonNull(nodeOptions, "nodeOptions").validate();

		DocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		TargetDefinitionProvider targetDefinitionProvider =
			new StaticTargetDefinitionProvider(List.<TargetDefinition>of());
		IndexerDefinitionProvider indexerDefinitionProvider =
			new StaticIndexerDefinitionProvider(new IndexerDefinition(
				new IndexDefinition("default", "v1", new JsonObject(), new JsonObject()),
				new QueueDefinition(new JsonObject())
			));
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerDocumentStore documentStore = new InMemoryIndexerDocumentStore();
		IndexerLifecycleEventBus lifecycleEventBus =
			new VertxIndexerLifecycleEventBusProvider(
				vertx,
				nodeOptions.getLifecycleEventBusOptions()
			).create(
				nodeOptions.getLifecycleEventBusConfig()
			);
		InvalidRouteCache invalidRouteCache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		TargetInvalidationNodeOptions targetInvalidationNodeOptions =
			nodeOptions.getTargetInvalidationOptions();
		TargetInvalidationRegistryOptions targetInvalidationOptions =
			targetInvalidationNodeOptions.registryOptions();
		TargetInvalidationRegistryConfig targetInvalidationConfig =
			targetInvalidationNodeOptions.registryConfig();
		TargetInvalidationRegistry targetInvalidationRegistryBackend = switch (
			targetInvalidationNodeOptions.getProvider()
		) {
			case IN_MEMORY -> new InMemoryTargetInvalidationRegistryProvider()
				.create(targetInvalidationConfig);
			case VERTX_SHARED_DATA -> new VertxSharedDataTargetInvalidationRegistryProvider(vertx)
				.create(targetInvalidationConfig);
		};
		TargetInvalidationRegistry targetInvalidationRegistry =
			TargetInvalidationRegistryServices.proxy(
				vertx,
				TargetInvalidationRegistryServices.address(
					targetInvalidationNodeOptions.getNamespace()
				)
			);
		MetadataChangeNotifier metadataChangeNotifier = new MetadataChangeNotifier(
			targetInvalidationRegistry,
			lifecycleEventBus
		);
		IndexerProviders indexerProviders = new IndexerProviders(List.of(
			new MetadataIndexerProvider(repository)
		));
		HotMetadataView hotMetadataView = new DefaultHotMetadataView(
			repository,
			targetDefinitionProvider,
			indexerProviders
		);
		IndexerOperations indexerOperations = new MetadataIndexerOperations(
			repository,
			metadataChangeNotifier
		);
		InMemoryCommandEngine commandEngine = new InMemoryCommandEngine();
		DocumentStoreCommandHandlers.register(
			commandEngine,
			new DocumentStoreCommandHandlers.Config(
				repository,
				documentStore,
				queue,
				metadataChangeNotifier,
				indexerOperations
			)
		);
		commandEngine.register(new SubmitIndexActionsCommandHandler(
			repository,
			targetDefinitionProvider,
			metadataChangeNotifier,
			queue,
			invalidRouteCache
		));
		HotIndexActionsService hotIndexActionsService = new HotIndexActionsService(
			hotMetadataView,
			new RoutedIndexActionPublisher(queue),
			commandEngine,
			invalidRouteCache
		);
		InvalidRouteMetadataChangeListener invalidRouteMetadataChangeListener =
			new InvalidRouteMetadataChangeListener(
				repository,
				lifecycleEventBus,
				invalidRouteCache
			);
		TargetInvalidationMetadataChangeListener targetInvalidationMetadataChangeListener =
			new TargetInvalidationMetadataChangeListener(
				lifecycleEventBus,
				hotMetadataView
			);
		TargetInvalidationPoller targetInvalidationPoller = new TargetInvalidationPoller(
			vertx,
			targetInvalidationRegistry,
			hotMetadataView,
			targetInvalidationOptions
		);
		IndexerRuntime runtime = new IndexerRuntime(
			vertx,
			queue,
			documentStore,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);
		IndexerRuntimeReconciler runtimeReconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			lifecycleEventBus,
			runtime,
			nodeOptions.getRuntimeReconcilerOptions()
		);

		return new IndexerNodeComponents(
			hotIndexActionsService,
			runtime,
			runtimeReconciler,
			commandEngine,
			indexerOperations,
			repository,
			lifecycleEventBus,
			queue,
			targetDefinitionProvider,
			indexerDefinitionProvider,
			documentStore,
			invalidRouteCache,
			invalidRouteMetadataChangeListener,
			targetInvalidationRegistryBackend,
			targetInvalidationRegistry,
			targetInvalidationMetadataChangeListener,
			targetInvalidationPoller
		);
	}
}
