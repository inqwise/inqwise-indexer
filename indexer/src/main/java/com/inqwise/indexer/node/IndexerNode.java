package com.inqwise.indexer.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.gateway.GatewayRequestHooks;
import com.inqwise.indexer.gateway.GatewayRestVerticle;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.hot.InvalidRouteMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationMetadataChangeListener;
import com.inqwise.indexer.hot.TargetInvalidationPoller;
import com.inqwise.indexer.rest.action.TargetActionRestVerticle;
import com.inqwise.indexer.rest.admin.AdminRestVerticle;
import com.inqwise.indexer.rest.runtime.RuntimeRestVerticle;
import com.inqwise.indexer.service.admin.AdminCreateRequestResolver;
import com.inqwise.indexer.service.admin.AdminServiceVerticle;
import com.inqwise.indexer.service.action.TargetActionServiceVerticle;
import com.inqwise.indexer.service.runtime.RuntimeServiceVerticle;
import com.inqwise.indexer.service.invalidation.TargetInvalidationRegistryServiceVerticle;
import com.inqwise.indexer.service.invalidation.TargetInvalidationRegistryServices;
import com.inqwise.indexer.rest.document.DocumentQueryRestVerticle;
import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.service.document.DocumentQueryServiceVerticle;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class IndexerNode {
	private static final Logger logger = LogManager.getLogger(IndexerNode.class);
	private static final DefaultIndexerNodeComponentsFactory DEFAULT_COMPONENTS_FACTORY =
		new DefaultIndexerNodeComponentsFactory();

	private final Vertx vertx;
	private final IndexerNodeOptions options;
	private final IndexerNodeComponents components;
	private final GatewayRequestHooks gatewayRequestHooks;
	private final List<String> deploymentIds = new ArrayList<>();
	private final List<String> dataPlaneDeploymentIds = new ArrayList<>();
	private final List<String> infrastructureDeploymentIds = new ArrayList<>();
	private boolean started;
	private boolean recoveryOnly;
	private boolean stopping;
	private Future<Void> recoveryFuture;

	public IndexerNode(
		Vertx vertx,
		IndexerNodeOptions options,
		IndexerNodeComponents components
	) {
		this(vertx, options, components, null);
	}

	public IndexerNode(
		Vertx vertx,
		IndexerNodeOptions options,
		IndexerNodeComponents components,
		GatewayRequestHooks gatewayRequestHooks
	) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		this.options = (
			options == null ? IndexerNodeOptions.builder().build() : options
		).validate();
		this.components = Objects.requireNonNull(components, "components");
		this.gatewayRequestHooks = gatewayRequestHooks;
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
			IndexerEventPublisher.NOOP
		);
	}

	public static IndexerNode create(
		Vertx vertx,
		IndexerNodeOptions options,
		GatewayRequestHooks gatewayRequestHooks,
		IndexerEventPublisher eventPublisher
	) {
		IndexerNodeOptions resolved = options == null
			? IndexerNodeOptions.builder().build()
			: options;
		resolved.validate();
		return new IndexerNode(
			vertx,
			resolved,
			DEFAULT_COMPONENTS_FACTORY.create(vertx, resolved, eventPublisher),
			gatewayRequestHooks
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
			.compose(ignored -> deployDocumentQuery())
			.compose(ignored -> deployDocumentQueryRest())
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

	private synchronized void trackDataPlaneDeployment(String deploymentId) {
		deploymentIds.add(deploymentId);
		dataPlaneDeploymentIds.add(deploymentId);
	}

	private synchronized void trackControlPlaneDeployment(String deploymentId) {
		deploymentIds.add(deploymentId);
	}

	private synchronized void trackInfrastructureDeployment(String deploymentId) {
		deploymentIds.add(deploymentId);
		infrastructureDeploymentIds.add(deploymentId);
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
			).onSuccess(this::trackInfrastructureDeployment).mapEmpty());
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

	private Future<Void> deployDocumentQuery() {
		IndexerServiceDeploymentOptions deployment = options.documentQuery();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		Future<Void> deployed = Future.succeededFuture();
		for (int i = 0; i < deployment.getInstances(); i++) {
			deployed = deployed.compose(ignored -> vertx.deployVerticle(
				new DocumentQueryServiceVerticle(components.documentQueryEngine()),
				new DeploymentOptions()
			).onSuccess(this::trackDataPlaneDeployment).mapEmpty());
		}
		return deployed;
	}

	private Future<Void> deployDocumentQueryRest() {
		IndexerServiceDeploymentOptions deployment = options.documentQueryRest();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(
			new DocumentQueryRestVerticle(options.getDocumentQueryRestOptions()),
			new DeploymentOptions()
		).onSuccess(this::trackDataPlaneDeployment).mapEmpty();
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

	private Future<Void> deployHealthRest() {
		IndexerServiceDeploymentOptions deployment = options.healthRest();
		if (!deployment.isEnabled()) {
			return Future.succeededFuture();
		}

		return vertx.deployVerticle(
			new NodeHealthRestVerticle(options.getHealthRestOptions(), this::isReady),
			new DeploymentOptions()
		).onSuccess(this::trackControlPlaneDeployment).mapEmpty();
	}

}
