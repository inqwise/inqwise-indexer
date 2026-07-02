package com.inqwise.indexer.node;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerEventPublisher;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerOptions;
import com.inqwise.indexer.IndexerRuntime;
import com.inqwise.indexer.IndexerRuntimeReconciler;
import com.inqwise.indexer.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.InMemoryIndexerQueue;
import com.inqwise.indexer.MetadataChangeNotifier;
import com.inqwise.indexer.VertxIndexerLifecycleEventBusProvider;
import com.inqwise.indexer.commands.DocumentStoreCommandHandlers;
import com.inqwise.indexer.commands.InMemoryCommandEngine;
import com.inqwise.indexer.commands.RoutedIndexActionPublisher;
import com.inqwise.indexer.commands.SubmitIndexActionsCommandHandler;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.definitions.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.definitions.StaticTargetDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinition;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.hot.DefaultHotMetadataView;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.hot.HotMetadataView;
import com.inqwise.indexer.hot.InMemoryInvalidRouteCache;
import com.inqwise.indexer.hot.InMemoryTargetInvalidationRegistryProvider;
import com.inqwise.indexer.hot.InvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationPoller;
import com.inqwise.indexer.hot.TargetInvalidationRegistry;
import com.inqwise.indexer.hot.TargetInvalidationRegistryConfig;
import com.inqwise.indexer.hot.TargetInvalidationRegistryOptions;
import com.inqwise.indexer.hot.VertxSharedDataTargetInvalidationRegistryProvider;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.operations.IndexerOperations;
import com.inqwise.indexer.operations.MetadataIndexerOperations;
import com.inqwise.indexer.providers.IndexerProviders;
import com.inqwise.indexer.providers.MetadataIndexerProvider;
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
				targetDefinitionProvider,
				indexerDefinitionProvider,
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
