package com.inqwise.indexer.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.gateway.GatewayRequestHooks;
import com.inqwise.indexer.gateway.GatewayRestVerticle;
import com.inqwise.indexer.adapters.local.InMemoryCommandEngine;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.hot.InvalidRouteMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationPoller;
import com.inqwise.indexer.rest.action.TargetActionRestVerticle;
import com.inqwise.indexer.rest.admin.AdminRestVerticle;
import com.inqwise.indexer.rest.runtime.RuntimeRestVerticle;
import com.inqwise.indexer.service.admin.AdminCreateRequestResolver;
import com.inqwise.indexer.service.admin.AdminInfrastructureItemView;
import com.inqwise.indexer.service.admin.AdminInfrastructureStatusResult;
import com.inqwise.indexer.service.admin.AdminNodeServiceView;
import com.inqwise.indexer.service.admin.AdminNodeStatusResult;
import com.inqwise.indexer.service.admin.AdminServices;
import com.inqwise.indexer.service.admin.AdminServiceVerticle;
import com.inqwise.indexer.service.action.TargetActionServiceVerticle;
import com.inqwise.indexer.service.action.TargetActionPreparationRegistry;
import com.inqwise.indexer.service.action.TargetActionServices;
import com.inqwise.indexer.service.runtime.RuntimeServiceVerticle;
import com.inqwise.indexer.service.runtime.RuntimeServices;
import com.inqwise.indexer.service.invalidation.TargetInvalidationRegistryServiceVerticle;
import com.inqwise.indexer.service.invalidation.TargetInvalidationRegistryServices;
import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.monitoring.IndexerOperationalMonitor;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class IndexerNode {
	private static final Logger logger = LogManager.getLogger(IndexerNode.class);
	private static final DefaultIndexerNodeComponentsFactory DEFAULT_COMPONENTS_FACTORY =
		new DefaultIndexerNodeComponentsFactory();

	private final Vertx vertx;
	private final IndexerNodeOptions options;
	private final IndexerNodeComponents components;
	private final GatewayRequestHooks gatewayRequestHooks;
	private final IndexerOperationalMonitor operationalMonitor;
	private final TargetActionPreparationRegistry targetActionPreparations;
	private final List<String> deploymentIds = new ArrayList<>();
	private final List<String> dataPlaneDeploymentIds = new ArrayList<>();
	private final List<String> infrastructureDeploymentIds = new ArrayList<>();
	private final Map<String, Integer> deployedServices = new LinkedHashMap<>();
	private final Map<String, String> serviceByDeploymentId = new LinkedHashMap<>();
	private boolean started;
	private boolean recoveryOnly;
	private boolean stopping;
	private Future<Void> recoveryFuture;

	public IndexerNode(
		Vertx vertx,
		IndexerNodeOptions options,
		IndexerNodeComponents components
	) {
		this(
			vertx,
			options,
			components,
			null,
			IndexerOperationalMonitor.NOOP
		);
	}

	public IndexerNode(
		Vertx vertx,
		IndexerNodeOptions options,
		IndexerNodeComponents components,
		GatewayRequestHooks gatewayRequestHooks
	) {
		this(
			vertx,
			options,
			components,
			gatewayRequestHooks,
			IndexerOperationalMonitor.NOOP
		);
	}

	public IndexerNode(
		Vertx vertx,
		IndexerNodeOptions options,
		IndexerNodeComponents components,
		GatewayRequestHooks gatewayRequestHooks,
		IndexerOperationalMonitor operationalMonitor
	) {
		this(
			vertx,
			options,
			components,
			gatewayRequestHooks,
			operationalMonitor,
			TargetActionPreparationRegistry.NONE
		);
	}

	public IndexerNode(
		Vertx vertx,
		IndexerNodeOptions options,
		IndexerNodeComponents components,
		GatewayRequestHooks gatewayRequestHooks,
		IndexerOperationalMonitor operationalMonitor,
		TargetActionPreparationRegistry targetActionPreparations
	) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		this.options = (
			options == null ? IndexerNodeOptions.builder().build() : options
		).validate();
		this.components = Objects.requireNonNull(components, "components");
		this.gatewayRequestHooks = gatewayRequestHooks;
		this.operationalMonitor = operationalMonitor == null
			? IndexerOperationalMonitor.NOOP
			: operationalMonitor;
		this.targetActionPreparations = Objects.requireNonNull(
			targetActionPreparations,
			"targetActionPreparations"
		);
		this.components.runtimeReconciler().onFailure(this::enterRecoveryOnly);
	}

	public static IndexerNode create(Vertx vertx, IndexerNodeOptions options) {
		return create(vertx, options, null);
	}

	public static IndexerNode create(
		Vertx vertx,
		IndexerNodeOptions options,
		GatewayRequestHooks gatewayRequestHooks
	) {
		return create(
			vertx,
			options,
			gatewayRequestHooks,
			IndexerEventPublisher.NOOP,
			IndexerOperationalMonitor.NOOP
		);
	}

	public static IndexerNode create(
		Vertx vertx,
		IndexerNodeOptions options,
		GatewayRequestHooks gatewayRequestHooks,
		IndexerEventPublisher eventPublisher
	) {
		return create(
			vertx,
			options,
			gatewayRequestHooks,
			eventPublisher,
			IndexerOperationalMonitor.NOOP
		);
	}

	public static IndexerNode create(
		Vertx vertx,
		IndexerNodeOptions options,
		GatewayRequestHooks gatewayRequestHooks,
		IndexerEventPublisher eventPublisher,
		IndexerOperationalMonitor operationalMonitor
	) {
		return create(
			vertx,
			options,
			gatewayRequestHooks,
			eventPublisher,
			operationalMonitor,
			TargetActionPreparationRegistry.NONE
		);
	}

	public static IndexerNode create(
		Vertx vertx,
		IndexerNodeOptions options,
		GatewayRequestHooks gatewayRequestHooks,
		IndexerEventPublisher eventPublisher,
		IndexerOperationalMonitor operationalMonitor,
		TargetActionPreparationRegistry targetActionPreparations
	) {
		return create(
			vertx,
			options,
			gatewayRequestHooks,
			eventPublisher,
			operationalMonitor,
			targetActionPreparations,
			IndexerPluginFactory.NONE
		);
	}

	public static IndexerNode create(
		Vertx vertx,
		IndexerNodeOptions options,
		GatewayRequestHooks gatewayRequestHooks,
		IndexerEventPublisher eventPublisher,
		IndexerOperationalMonitor operationalMonitor,
		TargetActionPreparationRegistry targetActionPreparations,
		IndexerPluginFactory pluginFactory
	) {
		IndexerNodeOptions resolved = options == null
			? IndexerNodeOptions.builder().build()
			: options;
		resolved.validate();
		return new IndexerNode(
			vertx,
			resolved,
			DEFAULT_COMPONENTS_FACTORY.create(
				vertx,
				resolved,
				eventPublisher,
				operationalMonitor,
				null,
				pluginFactory
			),
			gatewayRequestHooks,
			operationalMonitor,
			targetActionPreparations
		);
	}

	public Future<Void> start() {
		options.validate();
		synchronized (this) {
			started = false;
			stopping = false;
			recoveryOnly = false;
		}
		Future<Void> deployed = Future.succeededFuture();
		deployed = deployed.compose(ignored -> deployTargetInvalidationRegistry());
		deployed = deployed.compose(ignored -> components.commandEngine().start());
		deployed = deployed.compose(ignored -> startInvalidRouteMetadataChangeListener());
		deployed = deployed.compose(ignored -> startTargetInvalidationMetadataChangeListener());
		deployed = deployed.compose(ignored -> startTargetInvalidationPoller());
		deployed = deployed.compose(ignored -> deployAdmin());
		deployed = deployed.compose(ignored -> deployAdminRest());
		deployed = deployed.compose(ignored -> components.runtimeReconciler().start());
		deployed = deployed.compose(ignored -> deployDataPlane());
		deployed = deployed.compose(ignored -> deployHealthRest());
		return deployed.onSuccess(ignored -> {
			synchronized (this) {
				started = true;
			}
		});
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
		List<String> infrastructureDeployments;
		synchronized (this) {
			started = false;
			stopping = true;
			infrastructureDeployments = List.copyOf(infrastructureDeploymentIds);
			deployments = deploymentIds.stream()
				.filter(id -> !infrastructureDeploymentIds.contains(id))
				.toList();
		}

		return undeploy(deployments)
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
			.compose(ignored -> undeploy(infrastructureDeployments))
			.onComplete(ignored -> {
				synchronized (this) {
					deploymentIds.clear();
					dataPlaneDeploymentIds.clear();
					infrastructureDeploymentIds.clear();
					deployedServices.clear();
					serviceByDeploymentId.clear();
					started = false;
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

	public synchronized boolean isReady() {
		return started && !stopping && !recoveryOnly;
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

	private synchronized void trackDataPlaneDeployment(String serviceName, String deploymentId) {
		deploymentIds.add(deploymentId);
		dataPlaneDeploymentIds.add(deploymentId);
		trackService(serviceName, deploymentId);
	}

	private synchronized void trackControlPlaneDeployment(String serviceName, String deploymentId) {
		deploymentIds.add(deploymentId);
		trackService(serviceName, deploymentId);
	}

	private synchronized void trackInfrastructureDeployment(String serviceName, String deploymentId) {
		deploymentIds.add(deploymentId);
		infrastructureDeploymentIds.add(deploymentId);
		trackService(serviceName, deploymentId);
	}

	private void trackService(String serviceName, String deploymentId) {
		serviceByDeploymentId.put(deploymentId, serviceName);
		deployedServices.merge(serviceName, 1, Integer::sum);
	}

	private Future<Void> undeploy(List<String> deployments) {
		Future<Void> undeployed = Future.succeededFuture();
		for (int i = deployments.size() - 1; i >= 0; i--) {
			String deploymentId = deployments.get(i);
			undeployed = undeployed.compose(ignored -> vertx.undeploy(deploymentId)
				.recover(error -> Future.succeededFuture()));
		}
		return undeployed;
	}

	private synchronized void removeDataPlaneDeployment(String deploymentId) {
		deploymentIds.remove(deploymentId);
		dataPlaneDeploymentIds.remove(deploymentId);
		removeServiceDeployment(deploymentId);
	}

	private void removeServiceDeployment(String deploymentId) {
		String serviceName = serviceByDeploymentId.remove(deploymentId);
		if (serviceName == null) {
			return;
		}
		deployedServices.computeIfPresent(serviceName, (ignored, count) ->
			count <= 1 ? null : count - 1
		);
	}

	private synchronized AdminNodeStatusResult nodeStatus() {
		int dataPlaneDeployments = dataPlaneDeploymentIds.size();
		int infrastructureDeployments = infrastructureDeploymentIds.size();
		int controlPlaneDeployments = deploymentIds.size()
			- dataPlaneDeployments
			- infrastructureDeployments;
		TargetInvalidationNodeOptions targetInvalidation =
			options.getTargetInvalidationOptions();
		return AdminNodeStatusResult.builder()
			.withStarted(started)
			.withReady(started && !stopping && !recoveryOnly)
			.withRecoveryOnly(recoveryOnly)
			.withStopping(stopping)
			.withClustered(vertx.isClustered())
			.withDeploymentCount(deploymentIds.size())
			.withControlPlaneDeployments(controlPlaneDeployments)
			.withDataPlaneDeployments(dataPlaneDeployments)
			.withInfrastructureDeployments(infrastructureDeployments)
			.withLifecycleEventNamespace(options.getLifecycleEventBusConfig().namespace())
			.withTargetInvalidationProvider(targetInvalidation.getProvider().name())
			.withTargetInvalidationNamespace(targetInvalidation.getNamespace())
			.withTargetInvalidationMaxTargets(targetInvalidation.getMaxTargets())
			.withServices(serviceViews())
			.build();
	}

	private List<AdminNodeServiceView> serviceViews() {
		return List.of(
			serviceView(IndexerNodeOptions.Services.ADMIN, "control-plane"),
			serviceView(IndexerNodeOptions.Services.ADMIN_REST, "control-plane"),
			serviceView(IndexerNodeOptions.Services.HEALTH_REST, "control-plane"),
			serviceView(IndexerNodeOptions.Services.TARGET_ACTION, "data-plane"),
			serviceView(IndexerNodeOptions.Services.TARGET_ACTION_REST, "data-plane"),
			serviceView(IndexerNodeOptions.Services.RUNTIME, "data-plane"),
			serviceView(IndexerNodeOptions.Services.RUNTIME_REST, "data-plane"),
			serviceView(IndexerNodeOptions.Services.GATEWAY, "data-plane"),
			serviceView(
				IndexerNodeOptions.Services.TARGET_INVALIDATION_REGISTRY,
				"infrastructure"
			)
		);
	}

	private AdminNodeServiceView serviceView(String name, String group) {
		IndexerServiceDeploymentOptions deployment = options.service(name);
		return AdminNodeServiceView.builder()
			.withName(name)
			.withGroup(group)
			.withEnabled(deployment.isEnabled())
			.withConfiguredInstances(deployment.getInstances())
			.withDeployedInstances(deployedServices.getOrDefault(name, 0))
			.build();
	}

	private synchronized AdminInfrastructureStatusResult infrastructureStatus() {
		TargetInvalidationNodeOptions targetInvalidation =
			options.getTargetInvalidationOptions();
		List<AdminInfrastructureItemView> items = new ArrayList<>();
		items.add(infrastructureItem(
			"command-engine",
			"command",
			components.commandEngine(),
			commandEngineDetails()
		));
		items.add(infrastructureItem(
			"metadata-repository",
			"persistence",
			components.repository(),
			new JsonObject()
		));
		items.add(infrastructureItem(
			"queue-resources",
			"resources",
			components.queueResources(),
			new JsonObject()
		));
		items.add(infrastructureItem(
			"document-index-resources",
			"resources",
			components.documentIndexResources(),
			new JsonObject()
		));
		items.add(infrastructureItem(
			"lifecycle-event-bus",
			"events",
			components.lifecycleEventBus(),
			new JsonObject()
				.put("namespace", options.getLifecycleEventBusConfig().namespace())
				.put("clustered", vertx.isClustered())
		));
		items.add(infrastructureItem(
			"target-invalidation-registry",
			"invalidation",
			components.targetInvalidationRegistryBackend(),
			new JsonObject()
				.put("provider", targetInvalidation.getProvider().name())
				.put("namespace", targetInvalidation.getNamespace())
				.put("max_targets", targetInvalidation.getMaxTargets())
				.put("service_address", TargetInvalidationRegistryServices.address(
					targetInvalidation.getNamespace()
				))
		));
		items.add(infrastructureItem(
			"target-definitions",
			"definitions",
			components.targetDefinitionProvider(),
			new JsonObject()
		));
		items.add(infrastructureItem(
			"indexer-definitions",
			"definitions",
			components.indexerDefinitionProvider(),
			new JsonObject()
		));
		items.add(infrastructureItem(
			"invalid-route-cache",
			"routing",
			components.invalidRouteCache(),
			new JsonObject()
		));
		items.add(infrastructureItem(
			"admin-service-proxy",
			"service-proxy",
			AdminServices.class,
			new JsonObject().put("address", AdminServices.DEFAULT_ADDRESS)
		));
		items.add(infrastructureItem(
			"target-action-service-proxy",
			"service-proxy",
			TargetActionServices.class,
			new JsonObject().put("address", TargetActionServices.DEFAULT_ADDRESS)
		));
		items.add(infrastructureItem(
			"runtime-service-proxy",
			"service-proxy",
			RuntimeServices.class,
			new JsonObject().put("address", RuntimeServices.DEFAULT_ADDRESS)
		));
		items.add(infrastructureItem(
			"admin-rest",
			"rest",
			options.getAdminRestOptions(),
			restDetails(options.adminRest(), options.getAdminRestOptions().toJson())
		));
		items.add(infrastructureItem(
			"target-action-rest",
			"rest",
			options.getTargetActionRestOptions(),
			restDetails(options.targetActionRest(), options.getTargetActionRestOptions().toJson())
		));
		items.add(infrastructureItem(
			"runtime-rest",
			"rest",
			options.getRuntimeRestOptions(),
			restDetails(options.runtimeRest(), options.getRuntimeRestOptions().toJson())
		));
		items.add(infrastructureItem(
			"health-rest",
			"rest",
			options.getHealthRestOptions(),
			restDetails(options.healthRest(), options.getHealthRestOptions().toJson())
		));
		items.add(infrastructureItem(
			"gateway-rest",
			"rest",
			options.getGatewayOptions(),
			restDetails(options.gateway(), options.getGatewayOptions().toJson())
		));
		return AdminInfrastructureStatusResult.builder()
			.withItems(items)
			.build();
	}

	private JsonObject commandEngineDetails() {
		JsonObject details = new JsonObject();
		if (components.commandEngine() instanceof InMemoryCommandEngine inMemory) {
			details.put("started", inMemory.isStarted());
		}
		return details;
	}

	private static JsonObject restDetails(
		IndexerServiceDeploymentOptions deployment,
		JsonObject config
	) {
		return config.copy()
			.put("enabled", deployment.isEnabled())
			.put("configured_instances", deployment.getInstances());
	}

	private static AdminInfrastructureItemView infrastructureItem(
		String name,
		String category,
		Object implementation,
		JsonObject details
	) {
		return AdminInfrastructureItemView.builder()
			.withName(name)
			.withCategory(category)
			.withImplementation(implementationName(implementation))
			.withDetails(details)
			.build();
	}

	private static String implementationName(Object implementation) {
		if (implementation instanceof Class<?> type) {
			return type.getName();
		}
		return implementation.getClass().getName();
	}

	public IndexerNodeComponents components() {
		return components;
	}

	private Future<Void> deployTargetInvalidationRegistry() {
		IndexerServiceDeploymentOptions deployment = options.targetInvalidationRegistry();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		Future<Void> deployed = Future.succeededFuture();
		for (int i = 0; i < deployment.getInstances(); i++) {
			deployed = deployed.compose(ignored -> vertx.deployVerticle(
				new TargetInvalidationRegistryServiceVerticle(
					components.targetInvalidationRegistryBackend(),
					TargetInvalidationRegistryServices.address(
						options.getTargetInvalidationOptions().getNamespace()
					)
				),
				new DeploymentOptions()
			).onSuccess(id -> trackInfrastructureDeployment(
				IndexerNodeOptions.Services.TARGET_INVALIDATION_REGISTRY,
				id
			)).mapEmpty());
		}
		return deployed;
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
					new MetadataChangeNotifier(
						components.targetInvalidationRegistry(),
						components.lifecycleEventBus()
					),
					components.queueResources(),
					components.targetDefinitionProvider(),
					components.indexerDefinitionProvider(),
					components.documentIndexResources(),
					components.commandEngine(),
					components.indexerOperations(),
					operationalMonitor,
					components.invalidRouteCache(),
					components.targetInvalidationRegistry(),
					this::nodeStatus,
					this::infrastructureStatus
				),
				new DeploymentOptions()
			).onSuccess(id -> trackControlPlaneDeployment(
				IndexerNodeOptions.Services.ADMIN,
				id
			)).mapEmpty());
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
		).onSuccess(id -> trackControlPlaneDeployment(
			IndexerNodeOptions.Services.ADMIN_REST,
			id
		)).mapEmpty();
	}

	private Future<Void> deployTargetAction() {
		IndexerServiceDeploymentOptions deployment = options.targetAction();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		Future<Void> deployed = Future.succeededFuture();
		for (int i = 0; i < deployment.getInstances(); i++) {
			deployed = deployed.compose(ignored -> vertx.deployVerticle(
				new TargetActionServiceVerticle(
					components.hotIndexActionsService(),
					operationalMonitor,
					targetActionPreparations
				),
				new DeploymentOptions()
			).onSuccess(id -> trackDataPlaneDeployment(
				IndexerNodeOptions.Services.TARGET_ACTION,
				id
			)).mapEmpty());
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
		).onSuccess(id -> trackDataPlaneDeployment(
			IndexerNodeOptions.Services.TARGET_ACTION_REST,
			id
		)).mapEmpty();
	}

	private Future<Void> deployGateway() {
		IndexerServiceDeploymentOptions deployment = options.gateway();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		GatewayRestVerticle gateway = gatewayRequestHooks == null
			? new GatewayRestVerticle(options.getGatewayOptions())
			: new GatewayRestVerticle(options.getGatewayOptions(), gatewayRequestHooks);
		return vertx.deployVerticle(
			gateway,
			new DeploymentOptions()
		).onSuccess(id -> trackDataPlaneDeployment(
			IndexerNodeOptions.Services.GATEWAY,
			id
		)).mapEmpty();
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
		).onSuccess(id -> trackDataPlaneDeployment(
			IndexerNodeOptions.Services.RUNTIME,
			id
		)).mapEmpty();
	}

	private Future<Void> deployRuntimeRest() {
		IndexerServiceDeploymentOptions deployment = options.runtimeRest();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(
			new RuntimeRestVerticle(options.getRuntimeRestOptions()),
			new DeploymentOptions()
		).onSuccess(id -> trackDataPlaneDeployment(
			IndexerNodeOptions.Services.RUNTIME_REST,
			id
		)).mapEmpty();
	}

	private Future<Void> deployHealthRest() {
		IndexerServiceDeploymentOptions deployment = options.healthRest();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(
			new NodeHealthRestVerticle(options.getHealthRestOptions(), this::isReady),
			new DeploymentOptions()
		).onSuccess(id -> trackControlPlaneDeployment(
			IndexerNodeOptions.Services.HEALTH_REST,
			id
		)).mapEmpty();
	}

}
