package com.inqwise.indexer.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBusConfig;
import com.inqwise.indexer.runtime.IndexerRuntimeReconcilerOptions;
import com.inqwise.indexer.lifecycle.VertxIndexerLifecycleEventBusOptions;
import com.inqwise.indexer.gateway.GatewayRestOptions;
import com.inqwise.indexer.rest.action.TargetActionRestOptions;
import com.inqwise.indexer.rest.admin.AdminRestOptions;
import com.inqwise.indexer.rest.runtime.RuntimeRestOptions;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerNodeOptions {
	public static final class Keys {
		public static final String SERVICES = "services";
		public static final String ADMIN_REST = "admin_rest";
		public static final String TARGET_ACTION_REST = "target_action_rest";
		public static final String RUNTIME_REST = "runtime_rest";
		public static final String RUNTIME_RECONCILER = "runtime_reconciler";
		public static final String LIFECYCLE_EVENTS = "lifecycle_events";
		public static final String TARGET_INVALIDATION = "target_invalidation";
		public static final String GATEWAY = "gateway";

		private Keys() {
		}
	}

	public static final class LifecycleEvents {
		public static final String NAMESPACE = "namespace";
		public static final String DEFAULT_NAMESPACE = "local";

		private LifecycleEvents() {
		}
	}

	public static final class Services {
		public static final String ADMIN = "admin";
		public static final String ADMIN_REST = "adminRest";
		public static final String TARGET_ACTION = "targetAction";
		public static final String TARGET_ACTION_REST = "targetActionRest";
		public static final String RUNTIME = "runtime";
		public static final String RUNTIME_REST = "runtimeRest";
		public static final String GATEWAY = "gateway";
		public static final String TARGET_INVALIDATION_REGISTRY = "targetInvalidationRegistry";

		private static final Set<String> ALL = Set.of(
			ADMIN,
			ADMIN_REST,
			TARGET_ACTION,
			TARGET_ACTION_REST,
			RUNTIME,
			RUNTIME_REST,
			GATEWAY,
			TARGET_INVALIDATION_REGISTRY
		);

		private Services() {
		}
	}

	private final Map<String, IndexerServiceDeploymentOptions> services = new LinkedHashMap<>();
	private AdminRestOptions adminRestOptions = AdminRestOptions.builder().build();
	private TargetActionRestOptions targetActionRestOptions =
		TargetActionRestOptions.builder().build();
	private RuntimeRestOptions runtimeRestOptions = RuntimeRestOptions.builder().build();
	private IndexerRuntimeReconcilerOptions runtimeReconcilerOptions =
		new IndexerRuntimeReconcilerOptions();
	private IndexerLifecycleEventBusConfig lifecycleEventBusConfig =
		IndexerLifecycleEventBusConfig.builder()
			.withNamespace(LifecycleEvents.DEFAULT_NAMESPACE)
			.build();
	private VertxIndexerLifecycleEventBusOptions lifecycleEventBusOptions =
		new VertxIndexerLifecycleEventBusOptions();
	private GatewayRestOptions gatewayOptions = new GatewayRestOptions();
	private TargetInvalidationNodeOptions targetInvalidationOptions =
		TargetInvalidationNodeOptions.builder().build();

	public IndexerNodeOptions() {
		addDefaults();
	}

	public IndexerNodeOptions(JsonObject json) {
		addDefaults();
		JsonObject serviceJson = json.getJsonObject(Keys.SERVICES, new JsonObject());
		for (String serviceName : serviceJson.fieldNames()) {
			requireKnownService(serviceName);
			services.put(
				serviceName,
				new IndexerServiceDeploymentOptions(serviceJson.getJsonObject(serviceName, new JsonObject()))
			);
		}
		this.adminRestOptions = new AdminRestOptions(
			json.getJsonObject(Keys.ADMIN_REST, new JsonObject())
		);
		this.targetActionRestOptions = new TargetActionRestOptions(
			json.getJsonObject(Keys.TARGET_ACTION_REST, new JsonObject())
		);
		this.runtimeRestOptions = new RuntimeRestOptions(
			json.getJsonObject(Keys.RUNTIME_REST, new JsonObject())
		);
		this.runtimeReconcilerOptions = new IndexerRuntimeReconcilerOptions(
			json.getJsonObject(Keys.RUNTIME_RECONCILER, new JsonObject())
		);
		JsonObject lifecycleEvents = json.getJsonObject(
			Keys.LIFECYCLE_EVENTS,
			new JsonObject()
		);
		this.lifecycleEventBusConfig = IndexerLifecycleEventBusConfig.builder()
			.withNamespace(lifecycleEvents.getString(
				LifecycleEvents.NAMESPACE,
				LifecycleEvents.DEFAULT_NAMESPACE
			))
			.build();
		this.lifecycleEventBusOptions = new VertxIndexerLifecycleEventBusOptions(
			lifecycleEvents
		);
		this.gatewayOptions = new GatewayRestOptions(json.getJsonObject(Keys.GATEWAY, new JsonObject()));
		this.targetInvalidationOptions = new TargetInvalidationNodeOptions(
			json.getJsonObject(Keys.TARGET_INVALIDATION, new JsonObject())
		);
		validate();
	}

	public JsonObject toJson() {
		JsonObject serviceJson = new JsonObject();
		for (Map.Entry<String, IndexerServiceDeploymentOptions> entry : services.entrySet()) {
			serviceJson.put(entry.getKey(), entry.getValue().toJson());
		}

		return new JsonObject()
			.put(Keys.SERVICES, serviceJson)
			.put(Keys.ADMIN_REST, adminRestOptions.toJson())
			.put(Keys.TARGET_ACTION_REST, targetActionRestOptions.toJson())
			.put(Keys.RUNTIME_REST, runtimeRestOptions.toJson())
			.put(Keys.RUNTIME_RECONCILER, runtimeReconcilerOptions.toJson())
			.put(
				Keys.LIFECYCLE_EVENTS,
				lifecycleEventBusOptions.toJson().put(
					LifecycleEvents.NAMESPACE,
					lifecycleEventBusConfig.namespace()
				)
			)
			.put(Keys.GATEWAY, gatewayOptions.toJson())
			.put(Keys.TARGET_INVALIDATION, targetInvalidationOptions.toJson());
	}

	public IndexerServiceDeploymentOptions service(String name) {
		requireKnownService(name);
		return services.get(name);
	}

	public IndexerServiceDeploymentOptions targetAction() {
		return service(Services.TARGET_ACTION);
	}

	public IndexerServiceDeploymentOptions targetActionRest() {
		return service(Services.TARGET_ACTION_REST);
	}

	public IndexerServiceDeploymentOptions admin() {
		return service(Services.ADMIN);
	}

	public IndexerServiceDeploymentOptions adminRest() {
		return service(Services.ADMIN_REST);
	}

	public IndexerServiceDeploymentOptions runtime() {
		return service(Services.RUNTIME);
	}

	public IndexerServiceDeploymentOptions runtimeRest() {
		return service(Services.RUNTIME_REST);
	}

	public IndexerServiceDeploymentOptions gateway() {
		return service(Services.GATEWAY);
	}

	public IndexerServiceDeploymentOptions targetInvalidationRegistry() {
		return service(Services.TARGET_INVALIDATION_REGISTRY);
	}

	public AdminRestOptions getAdminRestOptions() {
		return adminRestOptions;
	}

	public IndexerNodeOptions setAdminRestOptions(
		AdminRestOptions adminRestOptions
	) {
		this.adminRestOptions = adminRestOptions == null
			? AdminRestOptions.builder().build()
			: adminRestOptions;
		return this;
	}

	public TargetActionRestOptions getTargetActionRestOptions() {
		return targetActionRestOptions;
	}

	public IndexerNodeOptions setTargetActionRestOptions(
		TargetActionRestOptions targetActionRestOptions
	) {
		this.targetActionRestOptions = targetActionRestOptions == null
			? TargetActionRestOptions.builder().build()
			: targetActionRestOptions;
		return this;
	}

	public GatewayRestOptions getGatewayOptions() {
		return gatewayOptions;
	}

	public RuntimeRestOptions getRuntimeRestOptions() {
		return runtimeRestOptions;
	}

	public IndexerNodeOptions setRuntimeRestOptions(RuntimeRestOptions runtimeRestOptions) {
		this.runtimeRestOptions = runtimeRestOptions == null
			? RuntimeRestOptions.builder().build()
			: runtimeRestOptions;
		return this;
	}

	public IndexerRuntimeReconcilerOptions getRuntimeReconcilerOptions() {
		return runtimeReconcilerOptions;
	}

	public IndexerNodeOptions setRuntimeReconcilerOptions(
		IndexerRuntimeReconcilerOptions runtimeReconcilerOptions
	) {
		this.runtimeReconcilerOptions = runtimeReconcilerOptions == null
			? new IndexerRuntimeReconcilerOptions()
			: runtimeReconcilerOptions;
		return this;
	}

	public IndexerLifecycleEventBusConfig getLifecycleEventBusConfig() {
		return lifecycleEventBusConfig;
	}

	public IndexerNodeOptions setLifecycleEventBusConfig(
		IndexerLifecycleEventBusConfig lifecycleEventBusConfig
	) {
		this.lifecycleEventBusConfig = lifecycleEventBusConfig == null
			? IndexerLifecycleEventBusConfig.builder()
				.withNamespace(LifecycleEvents.DEFAULT_NAMESPACE)
				.build()
			: lifecycleEventBusConfig;
		return this;
	}

	public VertxIndexerLifecycleEventBusOptions getLifecycleEventBusOptions() {
		return lifecycleEventBusOptions;
	}

	public IndexerNodeOptions setLifecycleEventBusOptions(
		VertxIndexerLifecycleEventBusOptions lifecycleEventBusOptions
	) {
		this.lifecycleEventBusOptions = lifecycleEventBusOptions == null
			? new VertxIndexerLifecycleEventBusOptions()
			: lifecycleEventBusOptions;
		return this;
	}

	public IndexerNodeOptions setGatewayOptions(GatewayRestOptions gatewayOptions) {
		this.gatewayOptions = gatewayOptions == null ? new GatewayRestOptions() : gatewayOptions;
		return this;
	}

	public TargetInvalidationNodeOptions getTargetInvalidationOptions() {
		return targetInvalidationOptions;
	}

	public IndexerNodeOptions setTargetInvalidationOptions(
		TargetInvalidationNodeOptions targetInvalidationOptions
	) {
		this.targetInvalidationOptions = targetInvalidationOptions == null
			? TargetInvalidationNodeOptions.builder().build()
			: targetInvalidationOptions;
		return this;
	}

	public Map<String, IndexerServiceDeploymentOptions> getServices() {
		return Map.copyOf(services);
	}

	public IndexerNodeOptions setService(
		String name,
		IndexerServiceDeploymentOptions options
	) {
		requireKnownService(name);
		services.put(name, options == null ? new IndexerServiceDeploymentOptions() : options);
		validate();
		return this;
	}

	public IndexerNodeOptions validate() {
		lifecycleEventBusOptions.validate();
		runtimeReconcilerOptions.validate();
		targetInvalidationOptions.validate();
		for (IndexerServiceDeploymentOptions options : services.values()) {
			options.validate();
		}

		IndexerServiceDeploymentOptions runtime = runtime();
		if (runtime.isEnabled() && runtime.getInstances() != 1) {
			throw new IllegalArgumentException("Runtime service must be deployed with exactly one instance");
		}

		IndexerServiceDeploymentOptions adminRest = adminRest();
		if (adminRest.isEnabled() && adminRest.getInstances() != 1) {
			throw new IllegalArgumentException(
				"Admin REST service must be deployed with exactly one instance"
			);
		}

		IndexerServiceDeploymentOptions targetActionRest = targetActionRest();
		if (targetActionRest.isEnabled() && targetActionRest.getInstances() != 1) {
			throw new IllegalArgumentException(
				"Target action REST service must be deployed with exactly one instance"
			);
		}

		IndexerServiceDeploymentOptions runtimeRest = runtimeRest();
		if (runtimeRest.isEnabled() && runtimeRest.getInstances() != 1) {
			throw new IllegalArgumentException("Runtime REST service must be deployed with exactly one instance");
		}

		IndexerServiceDeploymentOptions gateway = gateway();
		if (gateway.isEnabled() && gateway.getInstances() != 1) {
			throw new IllegalArgumentException("Gateway service must be deployed with exactly one instance");
		}

		IndexerServiceDeploymentOptions invalidationService = targetInvalidationRegistry();
		if (invalidationService.isEnabled()
			&& invalidationService.getInstances() > 1
			&& targetInvalidationOptions.getProvider()
				== TargetInvalidationNodeOptions.Provider.IN_MEMORY) {
			throw new IllegalArgumentException(
				"In-memory target invalidation service supports exactly one instance"
			);
		}

		return this;
	}

	private void addDefaults() {
		services.put(Services.ADMIN, new IndexerServiceDeploymentOptions());
		services.put(Services.ADMIN_REST, new IndexerServiceDeploymentOptions().setEnabled(false));
		services.put(Services.TARGET_ACTION, new IndexerServiceDeploymentOptions());
		services.put(Services.TARGET_ACTION_REST, new IndexerServiceDeploymentOptions().setEnabled(false));
		services.put(Services.RUNTIME, new IndexerServiceDeploymentOptions());
		services.put(Services.RUNTIME_REST, new IndexerServiceDeploymentOptions().setEnabled(false));
		services.put(Services.GATEWAY, new IndexerServiceDeploymentOptions().setEnabled(false));
		services.put(
			Services.TARGET_INVALIDATION_REGISTRY,
			new IndexerServiceDeploymentOptions()
		);
	}

	private void requireKnownService(String name) {
		if (!Services.ALL.contains(name)) {
			throw new IllegalArgumentException("Unknown indexer node service: " + name);
		}
	}
}
