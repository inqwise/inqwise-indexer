package com.inqwise.indexer.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.inqwise.indexer.IndexerLifecycleEventBusConfig;
import com.inqwise.indexer.IndexerRuntimeReconcilerOptions;
import com.inqwise.indexer.VertxIndexerLifecycleEventBusOptions;
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

		private static final Set<String> ALL = Set.of(
			ADMIN,
			ADMIN_REST,
			TARGET_ACTION,
			TARGET_ACTION_REST,
			RUNTIME,
			RUNTIME_REST,
			GATEWAY
		);

		private Services() {
		}
	}

	private final Map<String, IndexerServiceDeploymentOptions> services = new LinkedHashMap<>();
	private AdminRestOptions adminRestOptions = new AdminRestOptions();
	private TargetActionRestOptions targetActionRestOptions = new TargetActionRestOptions();
	private RuntimeRestOptions runtimeRestOptions = new RuntimeRestOptions();
	private IndexerRuntimeReconcilerOptions runtimeReconcilerOptions =
		new IndexerRuntimeReconcilerOptions();
	private IndexerLifecycleEventBusConfig lifecycleEventBusConfig =
		new IndexerLifecycleEventBusConfig(LifecycleEvents.DEFAULT_NAMESPACE);
	private VertxIndexerLifecycleEventBusOptions lifecycleEventBusOptions =
		new VertxIndexerLifecycleEventBusOptions();
	private GatewayRestOptions gatewayOptions = new GatewayRestOptions();

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
		this.lifecycleEventBusConfig = new IndexerLifecycleEventBusConfig(
			lifecycleEvents.getString(
				LifecycleEvents.NAMESPACE,
				LifecycleEvents.DEFAULT_NAMESPACE
			)
		);
		this.lifecycleEventBusOptions = new VertxIndexerLifecycleEventBusOptions(
			lifecycleEvents
		);
		this.gatewayOptions = new GatewayRestOptions(json.getJsonObject(Keys.GATEWAY, new JsonObject()));
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
			.put(Keys.GATEWAY, gatewayOptions.toJson());
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

	public AdminRestOptions getAdminRestOptions() {
		return adminRestOptions;
	}

	public IndexerNodeOptions setAdminRestOptions(
		AdminRestOptions adminRestOptions
	) {
		this.adminRestOptions = adminRestOptions == null
			? new AdminRestOptions()
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
			? new TargetActionRestOptions()
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
		this.runtimeRestOptions = runtimeRestOptions == null ? new RuntimeRestOptions() : runtimeRestOptions;
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
			? new IndexerLifecycleEventBusConfig(LifecycleEvents.DEFAULT_NAMESPACE)
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
	}

	private void requireKnownService(String name) {
		if (!Services.ALL.contains(name)) {
			throw new IllegalArgumentException("Unknown indexer node service: " + name);
		}
	}
}
