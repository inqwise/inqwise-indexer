package com.inqwise.indexer.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerNodeOptions {
	public static final class Keys {
		public static final String SERVICES = "services";

		private Keys() {
		}
	}

	public static final class Services {
		public static final String ADMIN = "admin";
		public static final String TARGET_ACTION = "targetAction";
		public static final String RUNTIME = "runtime";

		private static final Set<String> ALL = Set.of(
			ADMIN,
			TARGET_ACTION,
			RUNTIME
		);

		private Services() {
		}
	}

	private final Map<String, IndexerServiceDeploymentOptions> services = new LinkedHashMap<>();

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
		validate();
	}

	public JsonObject toJson() {
		JsonObject serviceJson = new JsonObject();
		for (Map.Entry<String, IndexerServiceDeploymentOptions> entry : services.entrySet()) {
			serviceJson.put(entry.getKey(), entry.getValue().toJson());
		}

		return new JsonObject().put(Keys.SERVICES, serviceJson);
	}

	public IndexerServiceDeploymentOptions service(String name) {
		requireKnownService(name);
		return services.get(name);
	}

	public IndexerServiceDeploymentOptions targetAction() {
		return service(Services.TARGET_ACTION);
	}

	public IndexerServiceDeploymentOptions admin() {
		return service(Services.ADMIN);
	}

	public IndexerServiceDeploymentOptions runtime() {
		return service(Services.RUNTIME);
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

		return this;
	}

	private void addDefaults() {
		services.put(Services.ADMIN, new IndexerServiceDeploymentOptions());
		services.put(Services.TARGET_ACTION, new IndexerServiceDeploymentOptions());
		services.put(Services.RUNTIME, new IndexerServiceDeploymentOptions());
	}

	private void requireKnownService(String name) {
		if (!Services.ALL.contains(name)) {
			throw new IllegalArgumentException("Unknown indexer node service: " + name);
		}
	}
}
