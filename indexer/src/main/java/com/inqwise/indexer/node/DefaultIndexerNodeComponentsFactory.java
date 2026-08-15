package com.inqwise.indexer.node;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.runtime.DocumentActionRuntimeHooks;
import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.monitoring.IndexerOperationalMonitor;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.runtime.IndexerOptions;
import com.inqwise.indexer.runtime.IndexerRuntime;
import com.inqwise.indexer.runtime.IndexerRuntimeReconciler;
import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.lifecycle.VertxIndexerLifecycleEventBusProvider;
import com.inqwise.indexer.cleanup.DocumentStoreCommandHandlers;
import com.inqwise.indexer.adapters.local.InMemoryCommandEngine;
import com.inqwise.indexer.provisioning.definitions.IndexDefinition;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinition;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.provisioning.definitions.QueueDefinition;
import com.inqwise.indexer.adapters.local.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.adapters.local.StaticTargetDefinitionProvider;
import com.inqwise.indexer.catalog.targets.TargetDefinitionProvider;
import com.inqwise.indexer.hot.DefaultHotMetadataView;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.hot.HotMetadataView;
import com.inqwise.indexer.adapters.local.InMemoryInvalidRouteCache;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistryProvider;
import com.inqwise.indexer.routing.InvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationPoller;
import com.inqwise.indexer.lifecycle.TargetInvalidationRegistry;
import com.inqwise.indexer.hot.TargetInvalidationRegistryConfig;
import com.inqwise.indexer.hot.TargetInvalidationRegistryOptions;
import com.inqwise.indexer.hot.VertxSharedDataTargetInvalidationRegistryProvider;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerOperations;
import com.inqwise.indexer.providers.IndexerProviders;
import com.inqwise.indexer.providers.IndexerPlugins;
import com.inqwise.indexer.providers.MetadataIndexerProvider;
import com.inqwise.indexer.provisioning.IndexerProvisioningService;
import com.inqwise.indexer.provisioning.MetadataIndexerProvisioningService;
import com.inqwise.indexer.publication.IndexPublicationService;
import com.inqwise.indexer.publication.MetadataIndexPublicationService;
import com.inqwise.indexer.routing.IndexerPublishingService;
import com.inqwise.indexer.routing.SubmitIndexActionsCommandHandler;
import com.inqwise.indexer.runtime.RuntimeIndexerPublishingService;
import com.inqwise.indexer.service.invalidation.TargetInvalidationRegistryServices;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public final class DefaultIndexerNodeComponentsFactory {
	public IndexerNodeComponents create(
		Vertx vertx,
		IndexerNodeOptions nodeOptions
	) {
		return create(
			vertx,
			nodeOptions,
			IndexerEventPublisher.NOOP,
			IndexerOperationalMonitor.NOOP
		);
	}

	public IndexerNodeComponents create(
		Vertx vertx,
		IndexerNodeOptions nodeOptions,
		IndexerEventPublisher eventPublisher
	) {
		return create(
			vertx,
			nodeOptions,
			eventPublisher,
			IndexerOperationalMonitor.NOOP
		);
	}

	public IndexerNodeComponents create(
		Vertx vertx,
		IndexerNodeOptions nodeOptions,
		IndexerEventPublisher eventPublisher,
		IndexerOperationalMonitor operationalMonitor
	) {
		return create(
			vertx,
			nodeOptions,
			eventPublisher,
			operationalMonitor,
			DocumentActionRuntimeHooks.NONE,
			IndexerPluginFactory.NONE
		);
	}

	public IndexerNodeComponents create(
		Vertx vertx,
		IndexerNodeOptions nodeOptions,
		IndexerEventPublisher eventPublisher,
		IndexerOperationalMonitor operationalMonitor,
		DocumentActionRuntimeHooks runtimeHooks
	) {
		return create(
			vertx,
			nodeOptions,
			eventPublisher,
			operationalMonitor,
			runtimeHooks,
			IndexerPluginFactory.NONE
		);
	}

	public IndexerNodeComponents create(
		Vertx vertx,
		IndexerNodeOptions nodeOptions,
		IndexerEventPublisher eventPublisher,
		IndexerOperationalMonitor operationalMonitor,
		DocumentActionRuntimeHooks runtimeHooks,
		IndexerPluginFactory pluginFactory
	) {
		Objects.requireNonNull(vertx, "vertx");
		Objects.requireNonNull(nodeOptions, "nodeOptions").validate();
		IndexerEventPublisher resolvedEventPublisher = eventPublisher == null
			? IndexerEventPublisher.NOOP
			: eventPublisher;
		IndexerOperationalMonitor resolvedOperationalMonitor =
			operationalMonitor == null
				? IndexerOperationalMonitor.NOOP
				: operationalMonitor;
		DocumentActionRuntimeHooks resolvedRuntimeHooks = runtimeHooks == null
			? DocumentActionRuntimeHooks.NONE
			: runtimeHooks;

		DocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		TargetDefinitionProvider targetDefinitionProvider =
			new StaticTargetDefinitionProvider(nodeOptions.targetDefinitions());
		IndexerDefinitionProvider indexerDefinitionProvider =
			new StaticIndexerDefinitionProvider(IndexerDefinition.builder()
				.withIndex(IndexDefinition.builder()
					.withSchemaName("default")
					.withSchemaVersion("v1")
					.withSettings(new JsonObject())
					.withMappings(new JsonObject())
					.build())
				.withQueue(QueueDefinition.builder()
					.withSettings(new JsonObject())
					.build())
				.build());
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
			DocumentStoreCommandHandlers.Config.builder()
				.withRepository(repository)
				.withDocumentIndexResources(documentStore)
				.withQueueResources(queue)
				.withMetadataChangeNotifier(metadataChangeNotifier)
				.withIndexerOperations(indexerOperations)
				.build()
		);
		IndexerPlugins plugins = Objects.requireNonNull(
			pluginFactory == null ? IndexerPluginFactory.NONE : pluginFactory,
			"pluginFactory"
		).create(IndexerPluginContext.builder()
			.withRepository(repository)
			.withQueue(queue)
			.withCommandEngine(commandEngine)
			.withLifecycleEventBus(lifecycleEventBus)
			.build());
		Objects.requireNonNull(plugins, "plugins");
		IndexerProvisioningService provisioningService = new MetadataIndexerProvisioningService(
			repository,
			indexerDefinitionProvider,
			documentStore,
			queue
		);
		IndexPublicationService publicationService = new MetadataIndexPublicationService(
			repository,
			indexerDefinitionProvider,
			documentStore,
			queue
		);
		IndexerRuntime runtime = new IndexerRuntime(
			vertx,
			queue,
			documentStore,
			IndexerOptions.builder().build(),
			resolvedEventPublisher,
			plugins,
			resolvedRuntimeHooks
		);
		IndexerPublishingService publishingService = new RuntimeIndexerPublishingService(runtime);
		commandEngine.register(new SubmitIndexActionsCommandHandler(
			repository,
			targetDefinitionProvider,
			provisioningService,
			publicationService,
			metadataChangeNotifier,
			publishingService,
			invalidRouteCache,
			plugins.actionReceiveCapabilities()
		));
		HotIndexActionsService hotIndexActionsService = new HotIndexActionsService(
			hotMetadataView,
			publishingService,
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
		IndexerRuntimeReconciler runtimeReconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			lifecycleEventBus,
			runtime,
			nodeOptions.getRuntimeReconcilerOptions(),
			resolvedOperationalMonitor
		);
		return IndexerNodeComponents.builder()
			.withHotIndexActionsService(hotIndexActionsService)
			.withRuntime(runtime)
			.withRuntimeReconciler(runtimeReconciler)
			.withCommandEngine(commandEngine)
			.withIndexerOperations(indexerOperations)
			.withRepository(repository)
			.withLifecycleEventBus(lifecycleEventBus)
			.withQueueResources(queue)
			.withTargetDefinitionProvider(targetDefinitionProvider)
			.withIndexerDefinitionProvider(indexerDefinitionProvider)
			.withDocumentIndexResources(documentStore)
			.withInvalidRouteCache(invalidRouteCache)
			.withInvalidRouteMetadataChangeListener(invalidRouteMetadataChangeListener)
			.withTargetInvalidationRegistryBackend(targetInvalidationRegistryBackend)
			.withTargetInvalidationRegistry(targetInvalidationRegistry)
			.withTargetInvalidationMetadataChangeListener(
				targetInvalidationMetadataChangeListener
			)
			.withTargetInvalidationPoller(targetInvalidationPoller)
			.build();
	}
}
