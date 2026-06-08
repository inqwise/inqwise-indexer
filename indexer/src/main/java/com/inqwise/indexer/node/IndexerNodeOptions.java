package com.inqwise.indexer.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.inqwise.indexer.gateway.GatewayRestOptions;
import com.inqwise.indexer.rest.action.TargetActionRestOptions;
import com.inqwise.indexer.rest.admin.AdminRestOptions;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerNodeOptions {
	public static final class Keys {
		public static final String SERVICES = "services";
		public static final String ADMIN_REST = "admin_rest";
		public static final String TARGET_ACTION_REST = "target_action_rest";
		public static final String GATEWAY = "gateway";

		private Keys() {
		}
	}

	public static final class Services {
		public static final String ADMIN = "admin";
		public static final String ADMIN_REST = "adminRest";
		public static final String TARGET_ACTION = "targetAction";
		public static final String TARGET_ACTION_REST = "targetActionRest";
		public static final String RUNTIME = "runtime";
		public static final String GATEWAY = "gateway";

		private static final Set<String> ALL = Set.of(
			ADMIN,
			ADMIN_REST,
			TARGET_ACTION,
			TARGET_ACTION_REST,
			RUNTIME,
			GATEWAY
		);

		private Services() {
		}
	}

	private final Map<String, IndexerServiceDeploymentOptions> services = new LinkedHashMap<>();
	private AdminRestOptions adminRestOptions = new AdminRestOptions();
	private TargetActionRestOptions targetActionRestOptions = new TargetActionRestOptions();
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
		services.put(Services.GATEWAY, new IndexerServiceDeploymentOptions().setEnabled(false));
	}

	private void requireKnownService(String name) {
		if (!Services.ALL.contains(name)) {
			throw new IllegalArgumentException("Unknown indexer node service: " + name);
		}
	}
}
