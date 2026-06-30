package com.inqwise.indexer.node;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerOptions;
import com.inqwise.indexer.IndexerRuntime;
import com.inqwise.indexer.IndexerRuntimeReconciler;
import com.inqwise.indexer.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.InMemoryIndexerQueue;
import com.inqwise.indexer.IndexerEventPublisher;
import com.inqwise.indexer.VertxIndexerLifecycleEventBusProvider;
import com.inqwise.indexer.commands.InMemoryCommandEngine;
import com.inqwise.indexer.commands.DocumentStoreCommandHandlers;
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
import com.inqwise.indexer.gateway.GatewayRestVerticle;
import com.inqwise.indexer.hot.DefaultHotMetadataView;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.hot.HotMetadataView;
import com.inqwise.indexer.hot.InMemoryInvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteMetadataChangeListener;
import com.inqwise.indexer.hot.InMemoryTargetInvalidationRegistryProvider;
import com.inqwise.indexer.hot.TargetInvalidationMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationPoller;
import com.inqwise.indexer.hot.TargetInvalidationRegistry;
import com.inqwise.indexer.hot.TargetInvalidationRegistryConfig;
import com.inqwise.indexer.hot.TargetInvalidationRegistryOptions;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.operations.IndexerOperations;
import com.inqwise.indexer.operations.MetadataIndexerOperations;
import com.inqwise.indexer.providers.IndexerProviders;
import com.inqwise.indexer.providers.MetadataIndexerProvider;
import com.inqwise.indexer.rest.action.TargetActionRestVerticle;
import com.inqwise.indexer.rest.admin.AdminRestVerticle;
import com.inqwise.indexer.rest.runtime.RuntimeRestVerticle;
import com.inqwise.indexer.service.admin.AdminCreateRequestResolver;
import com.inqwise.indexer.service.admin.AdminServiceVerticle;
import com.inqwise.indexer.service.action.TargetActionServiceVerticle;
import com.inqwise.indexer.service.runtime.RuntimeServiceVerticle;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class IndexerNode {
	private static final Logger logger = LogManager.getLogger(IndexerNode.class);

	private final Vertx vertx;
	private final IndexerNodeOptions options;
	private final IndexerNodeComponents components;
	private final List<String> deploymentIds = new ArrayList<>();
	private final List<String> dataPlaneDeploymentIds = new ArrayList<>();
	private boolean recoveryOnly;
	private boolean stopping;
	private Future<Void> recoveryFuture;

	public IndexerNode(
		Vertx vertx,
		IndexerNodeOptions options,
		IndexerNodeComponents components
	) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		this.options = (options == null ? new IndexerNodeOptions() : options).validate();
		this.components = Objects.requireNonNull(components, "components");
		this.components.runtimeReconciler().onFailure(this::enterRecoveryOnly);
	}

	public static IndexerNode create(Vertx vertx, IndexerNodeOptions options) {
		IndexerNodeOptions resolved = options == null ? new IndexerNodeOptions() : options;
		resolved.validate();
		return new IndexerNode(vertx, resolved, defaultComponents(vertx, resolved));
	}

	public Future<Void> start() {
		options.validate();
		synchronized (this) {
			stopping = false;
			recoveryOnly = false;
		}
		Future<Void> deployed = Future.succeededFuture();
		deployed = deployed.compose(ignored -> components.commandEngine().start());
		deployed = deployed.compose(ignored -> startInvalidRouteMetadataChangeListener());
		deployed = deployed.compose(ignored -> startTargetInvalidationMetadataChangeListener());
		deployed = deployed.compose(ignored -> startTargetInvalidationPoller());
		deployed = deployed.compose(ignored -> deployAdmin());
		deployed = deployed.compose(ignored -> deployAdminRest());
		deployed = deployed.compose(ignored -> components.runtimeReconciler().start());
		deployed = deployed.compose(ignored -> deployDataPlane());
		return deployed;
	}

	private Future<Void> deployDataPlane() {
		return deployTargetAction()
			.compose(ignored -> deployTargetActionRest())
			.compose(ignored -> deployRuntime())
			.compose(ignored -> deployRuntimeRest())
			.compose(ignored -> deployGateway());
	}

	private Future<Void> startInvalidRouteMetadataChangeListener() {
		InvalidRouteMetadataChangeListener listener =
			components.invalidRouteMetadataChangeListener();
		return listener == null ? Future.succeededFuture() : listener.start();
	}

	private Future<Void> startTargetInvalidationMetadataChangeListener() {
		TargetInvalidationMetadataChangeListener listener =
			components.targetInvalidationMetadataChangeListener();
		return listener == null ? Future.succeededFuture() : listener.start();
	}

	private Future<Void> startTargetInvalidationPoller() {
		TargetInvalidationPoller poller = components.targetInvalidationPoller();
		return poller == null ? Future.succeededFuture() : poller.start();
	}

	public Future<Void> stop() {
		List<String> deployments;
		synchronized (this) {
			stopping = true;
			deployments = List.copyOf(deploymentIds);
		}
		Future<Void> stopped = Future.succeededFuture();
		for (int i = deployments.size() - 1; i >= 0; i--) {
			String deploymentId = deployments.get(i);
			stopped = stopped.compose(ignored -> vertx.undeploy(deploymentId)
				.recover(error -> Future.succeededFuture()));
		}

		return stopped
			.compose(ignored -> components.runtimeReconciler().stop())
			.compose(ignored -> {
				TargetInvalidationPoller poller = components.targetInvalidationPoller();
				return poller == null ? Future.succeededFuture() : poller.stop();
			})
			.compose(ignored -> {
				TargetInvalidationMetadataChangeListener listener =
					components.targetInvalidationMetadataChangeListener();
				return listener == null ? Future.succeededFuture() : listener.stop();
			})
			.compose(ignored -> {
				InvalidRouteMetadataChangeListener listener =
					components.invalidRouteMetadataChangeListener();
				return listener == null ? Future.succeededFuture() : listener.stop();
			})
			.compose(ignored -> components.commandEngine().stop())
			.onComplete(ignored -> {
				synchronized (this) {
					deploymentIds.clear();
					dataPlaneDeploymentIds.clear();
					recoveryOnly = false;
					recoveryFuture = null;
				}
			});
	}

	public synchronized List<String> deploymentIds() {
		return List.copyOf(deploymentIds);
	}

	public synchronized boolean isRecoveryOnly() {
		return recoveryOnly;
	}

	public synchronized Future<Void> recover() {
		if (!recoveryOnly) {
			return Future.succeededFuture();
		}
		if (recoveryFuture != null) {
			return recoveryFuture;
		}

		Future<Void> recovering = components.runtimeReconciler().start()
			.compose(ignored -> deployDataPlane())
			.recover(error -> undeployDataPlane()
				.compose(ignored -> components.runtimeReconciler().stop())
				.compose(ignored -> Future.failedFuture(error)));
		recoveryFuture = recovering;
		recovering.onComplete(result -> {
			synchronized (this) {
				recoveryFuture = null;
				if (result.succeeded()) {
					recoveryOnly = false;
				}
			}
		});
		return recovering;
	}

	Future<Void> enterRecoveryOnly(Throwable error) {
		synchronized (this) {
			if (stopping || recoveryOnly) {
				return Future.succeededFuture();
			}
			recoveryOnly = true;
		}
		logger.error("Indexer node entered recovery-only mode", error);
		return undeployDataPlane();
	}

	private Future<Void> undeployDataPlane() {
		List<String> deployments;
		synchronized (this) {
			deployments = List.copyOf(dataPlaneDeploymentIds);
		}
		Future<Void> undeployed = Future.succeededFuture();
		for (int i = deployments.size() - 1; i >= 0; i--) {
			String deploymentId = deployments.get(i);
			undeployed = undeployed.compose(ignored -> vertx.undeploy(deploymentId)
				.recover(error -> Future.succeededFuture())
				.onComplete(result -> removeDataPlaneDeployment(deploymentId)));
		}
		return undeployed;
	}

	private synchronized void trackDataPlaneDeployment(String deploymentId) {
		deploymentIds.add(deploymentId);
		dataPlaneDeploymentIds.add(deploymentId);
	}

	private synchronized void trackControlPlaneDeployment(String deploymentId) {
		deploymentIds.add(deploymentId);
	}

	private synchronized void removeDataPlaneDeployment(String deploymentId) {
		deploymentIds.remove(deploymentId);
		dataPlaneDeploymentIds.remove(deploymentId);
	}

	public IndexerNodeComponents components() {
		return components;
	}

	private Future<Void> deployAdmin() {
		IndexerServiceDeploymentOptions deployment = options.admin();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		Future<Void> deployed = Future.succeededFuture();
		for (int i = 0; i < deployment.getInstances(); i++) {
			deployed = deployed.compose(ignored -> vertx.deployVerticle(
				new AdminServiceVerticle(
					components.repository(),
					components.lifecycleEventBus(),
					components.queueResources(),
					components.targetDefinitionProvider(),
					components.indexerDefinitionProvider(),
					components.documentIndexResources(),
					components.commandEngine(),
					components.indexerOperations()
				),
				new DeploymentOptions()
			).onSuccess(this::trackControlPlaneDeployment).mapEmpty());
		}

		return deployed;
	}

	private Future<Void> deployAdminRest() {
		IndexerServiceDeploymentOptions deployment = options.adminRest();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(
			new AdminRestVerticle(
				options.getAdminRestOptions(),
				new AdminCreateRequestResolver(components.repository())
			),
			new DeploymentOptions()
		).onSuccess(this::trackControlPlaneDeployment).mapEmpty();
	}

	private Future<Void> deployTargetAction() {
		IndexerServiceDeploymentOptions deployment = options.targetAction();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		Future<Void> deployed = Future.succeededFuture();
		for (int i = 0; i < deployment.getInstances(); i++) {
			deployed = deployed.compose(ignored -> vertx.deployVerticle(
				new TargetActionServiceVerticle(components.hotIndexActionsService()),
				new DeploymentOptions()
			).onSuccess(this::trackDataPlaneDeployment).mapEmpty());
		}

		return deployed;
	}

	private Future<Void> deployTargetActionRest() {
		IndexerServiceDeploymentOptions deployment = options.targetActionRest();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(
			new TargetActionRestVerticle(options.getTargetActionRestOptions()),
			new DeploymentOptions()
		).onSuccess(this::trackDataPlaneDeployment).mapEmpty();
	}

	private Future<Void> deployGateway() {
		IndexerServiceDeploymentOptions deployment = options.gateway();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(
			new GatewayRestVerticle(options.getGatewayOptions()),
			new DeploymentOptions()
		).onSuccess(this::trackDataPlaneDeployment).mapEmpty();
	}

	private Future<Void> deployRuntime() {
		IndexerServiceDeploymentOptions deployment = options.runtime();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(
			new RuntimeServiceVerticle(
				components.runtime(),
				components.runtimeReconciler()
			),
			new DeploymentOptions()
		).onSuccess(this::trackDataPlaneDeployment).mapEmpty();
	}

	private Future<Void> deployRuntimeRest() {
		IndexerServiceDeploymentOptions deployment = options.runtimeRest();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(
			new RuntimeRestVerticle(options.getRuntimeRestOptions()),
			new DeploymentOptions()
		).onSuccess(this::trackDataPlaneDeployment).mapEmpty();
	}

	private static IndexerNodeComponents defaultComponents(
		Vertx vertx,
		IndexerNodeOptions nodeOptions
	) {
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
		TargetInvalidationRegistryOptions targetInvalidationOptions =
			new TargetInvalidationRegistryOptions(Duration.ofSeconds(30), 3, 10_000);
		TargetInvalidationRegistry targetInvalidationRegistry =
			new InMemoryTargetInvalidationRegistryProvider().create(
				new TargetInvalidationRegistryConfig(
					"local",
					targetInvalidationOptions
				)
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
			lifecycleEventBus
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
				lifecycleEventBus,
				indexerOperations
			)
		);
		commandEngine.register(new SubmitIndexActionsCommandHandler(
				repository,
				targetDefinitionProvider,
				lifecycleEventBus,
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
				hotMetadataView,
				targetInvalidationRegistry
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
			targetInvalidationRegistry,
			targetInvalidationMetadataChangeListener,
			targetInvalidationPoller
		);
	}
}
