package com.inqwise.indexer.node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBusConfig;
import com.inqwise.indexer.runtime.IndexerRuntimeReconcilerOptions;
import com.inqwise.indexer.lifecycle.VertxIndexerLifecycleEventBusOptions;
import com.inqwise.indexer.gateway.GatewayRestOptions;
import com.inqwise.indexer.rest.action.TargetActionRestOptions;
import com.inqwise.indexer.rest.admin.AdminRestOptions;
import com.inqwise.indexer.rest.runtime.RuntimeRestOptions;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
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
		public static final String TARGET_DEFINITIONS = "target_definitions";
		public static final String GATEWAY = "gateway";

		private Keys() {
		}
	}

	public static final class TargetDefinitions {
		public static final String TARGET_NAME = "target_name";
		public static final String PERIOD_STRATEGY = "period_strategy";
		public static final String AUTO_PROVISION_ON_WRITE = "auto_provision_on_write";

		private TargetDefinitions() {
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
		IndexerRuntimeReconcilerOptions.builder().build();
	private IndexerLifecycleEventBusConfig lifecycleEventBusConfig =
		IndexerLifecycleEventBusConfig.builder()
			.withNamespace(LifecycleEvents.DEFAULT_NAMESPACE)
			.build();
	private VertxIndexerLifecycleEventBusOptions lifecycleEventBusOptions =
		VertxIndexerLifecycleEventBusOptions.builder().build();
	private GatewayRestOptions gatewayOptions = GatewayRestOptions.builder().build();
	private TargetInvalidationNodeOptions targetInvalidationOptions =
		TargetInvalidationNodeOptions.builder().build();
	private List<TargetDefinition> targetDefinitions = List.of();

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
		this.targetDefinitions = readTargetDefinitions(
			json.getJsonArray(Keys.TARGET_DEFINITIONS, new JsonArray())
		);
		validate();
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		JsonObject serviceJson = new JsonObject();
		for (Map.Entry<String, IndexerServiceDeploymentOptions> entry : services.entrySet()) {
			serviceJson.put(entry.getKey(), entry.getValue().toJson());
		}

		JsonArray targetDefinitionJson = new JsonArray();
		for (TargetDefinition definition : targetDefinitions) {
			targetDefinitionJson.add(new JsonObject()
				.put(TargetDefinitions.TARGET_NAME, definition.targetName())
				.put(TargetDefinitions.PERIOD_STRATEGY, definition.periodStrategy().name())
				.put(
					TargetDefinitions.AUTO_PROVISION_ON_WRITE,
					definition.autoProvisionOnWrite()
				));
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
			.put(Keys.TARGET_INVALIDATION, targetInvalidationOptions.toJson())
			.put(Keys.TARGET_DEFINITIONS, targetDefinitionJson);
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
			? IndexerRuntimeReconcilerOptions.builder().build()
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
			? VertxIndexerLifecycleEventBusOptions.builder().build()
			: lifecycleEventBusOptions;
		return this;
	}

	public IndexerNodeOptions setGatewayOptions(GatewayRestOptions gatewayOptions) {
		this.gatewayOptions = gatewayOptions == null
			? GatewayRestOptions.builder().build()
			: gatewayOptions;
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

	public List<TargetDefinition> targetDefinitions() {
		return List.copyOf(targetDefinitions);
	}

	public Map<String, IndexerServiceDeploymentOptions> getServices() {
		return Map.copyOf(services);
	}

	public IndexerNodeOptions setService(
		String name,
		IndexerServiceDeploymentOptions options
	) {
		requireKnownService(name);
		services.put(
			name,
			options == null ? IndexerServiceDeploymentOptions.builder().build() : options
		);
		validate();
		return this;
	}

	public IndexerNodeOptions validate() {
		lifecycleEventBusOptions.validate();
		runtimeReconcilerOptions.validate();
		targetInvalidationOptions.validate();
		validateTargetDefinitions(targetDefinitions);
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

	public static final class Builder {
		private final Map<String, IndexerServiceDeploymentOptions> services =
			new LinkedHashMap<>();
		private AdminRestOptions adminRestOptions;
		private TargetActionRestOptions targetActionRestOptions;
		private RuntimeRestOptions runtimeRestOptions;
		private IndexerRuntimeReconcilerOptions runtimeReconcilerOptions;
		private IndexerLifecycleEventBusConfig lifecycleEventBusConfig;
		private VertxIndexerLifecycleEventBusOptions lifecycleEventBusOptions;
		private GatewayRestOptions gatewayOptions;
		private TargetInvalidationNodeOptions targetInvalidationOptions;
		private List<TargetDefinition> targetDefinitions = List.of();

		private Builder() {
		}

		public Builder withService(String name, IndexerServiceDeploymentOptions value) {
			requireKnownService(name);
			services.put(name, copy(Objects.requireNonNull(value, "value")));
			return this;
		}

		public Builder withAdminRestOptions(AdminRestOptions value) {
			adminRestOptions = copy(Objects.requireNonNull(value, "value"));
			return this;
		}

		public Builder withTargetActionRestOptions(TargetActionRestOptions value) {
			targetActionRestOptions = copy(Objects.requireNonNull(value, "value"));
			return this;
		}

		public Builder withRuntimeRestOptions(RuntimeRestOptions value) {
			runtimeRestOptions = copy(Objects.requireNonNull(value, "value"));
			return this;
		}

		public Builder withRuntimeReconcilerOptions(
			IndexerRuntimeReconcilerOptions value
		) {
			runtimeReconcilerOptions = copy(Objects.requireNonNull(value, "value"));
			return this;
		}

		public Builder withLifecycleEventBusConfig(IndexerLifecycleEventBusConfig value) {
			lifecycleEventBusConfig = Objects.requireNonNull(value, "value");
			return this;
		}

		public Builder withLifecycleEventBusOptions(
			VertxIndexerLifecycleEventBusOptions value
		) {
			lifecycleEventBusOptions = copy(Objects.requireNonNull(value, "value"));
			return this;
		}

		public Builder withGatewayOptions(GatewayRestOptions value) {
			gatewayOptions = copy(Objects.requireNonNull(value, "value"));
			return this;
		}

		public Builder withTargetInvalidationOptions(TargetInvalidationNodeOptions value) {
			targetInvalidationOptions = copy(Objects.requireNonNull(value, "value"));
			return this;
		}

		public Builder withTargetDefinitions(Collection<TargetDefinition> value) {
			targetDefinitions = copyTargetDefinitions(value);
			return this;
		}

		public IndexerNodeOptions build() {
			IndexerNodeOptions options = new IndexerNodeOptions();
			for (Map.Entry<String, IndexerServiceDeploymentOptions> entry : services.entrySet()) {
				options.services.put(entry.getKey(), copy(entry.getValue()));
			}
			if (adminRestOptions != null) {
				options.adminRestOptions = copy(adminRestOptions);
			}
			if (targetActionRestOptions != null) {
				options.targetActionRestOptions = copy(targetActionRestOptions);
			}
			if (runtimeRestOptions != null) {
				options.runtimeRestOptions = copy(runtimeRestOptions);
			}
			if (runtimeReconcilerOptions != null) {
				options.runtimeReconcilerOptions = copy(runtimeReconcilerOptions);
			}
			if (lifecycleEventBusConfig != null) {
				options.lifecycleEventBusConfig = lifecycleEventBusConfig;
			}
			if (lifecycleEventBusOptions != null) {
				options.lifecycleEventBusOptions = copy(lifecycleEventBusOptions);
			}
			if (gatewayOptions != null) {
				options.gatewayOptions = copy(gatewayOptions);
			}
			if (targetInvalidationOptions != null) {
				options.targetInvalidationOptions = copy(targetInvalidationOptions);
			}
			options.targetDefinitions = copyTargetDefinitions(targetDefinitions);
			return options.validate();
		}
	}

	private static IndexerServiceDeploymentOptions copy(
		IndexerServiceDeploymentOptions value
	) {
		return IndexerServiceDeploymentOptions.builder()
			.withEnabled(value.isEnabled())
			.withInstances(value.getInstances())
			.build();
	}

	private static List<TargetDefinition> readTargetDefinitions(JsonArray values) {
		List<TargetDefinition> definitions = new ArrayList<>();
		for (int index = 0; index < values.size(); index++) {
			JsonObject value = values.getJsonObject(index);
			String strategyName = value.getString(
				TargetDefinitions.PERIOD_STRATEGY,
				TargetPeriodStrategy.NONE.name()
			);
			TargetPeriodStrategy periodStrategy;
			try {
				periodStrategy = TargetPeriodStrategy.valueOf(strategyName);
			} catch (IllegalArgumentException error) {
				throw new IllegalArgumentException(
					"Unknown target period strategy: " + strategyName,
					error
				);
			}
			definitions.add(TargetDefinition.builder()
				.withTargetName(value.getString(TargetDefinitions.TARGET_NAME))
				.withPeriodStrategy(periodStrategy)
				.withAutoProvisionOnWrite(value.getBoolean(
					TargetDefinitions.AUTO_PROVISION_ON_WRITE,
					false
				))
				.build());
		}
		validateTargetDefinitions(definitions);
		return List.copyOf(definitions);
	}

	private static List<TargetDefinition> copyTargetDefinitions(
		Collection<TargetDefinition> values
	) {
		Objects.requireNonNull(values, "targetDefinitions");
		return values.stream()
			.map(value -> TargetDefinition.builder()
				.withTargetName(Objects.requireNonNull(value, "targetDefinition").targetName())
				.withPeriodStrategy(value.periodStrategy())
				.withAutoProvisionOnWrite(value.autoProvisionOnWrite())
				.build())
			.toList();
	}

	private static void validateTargetDefinitions(Collection<TargetDefinition> values) {
		Set<String> targetNames = new HashSet<>();
		for (TargetDefinition definition : values) {
			if (!targetNames.add(definition.targetName())) {
				throw new IllegalArgumentException(
					"Duplicate target definition: " + definition.targetName()
				);
			}
		}
	}

	private static AdminRestOptions copy(AdminRestOptions value) {
		return AdminRestOptions.builder()
			.withHost(value.getHost())
			.withPort(value.getPort())
			.withOpenApiPath(value.getOpenApiPath())
			.build();
	}

	private static TargetActionRestOptions copy(TargetActionRestOptions value) {
		return TargetActionRestOptions.builder()
			.withHost(value.getHost())
			.withPort(value.getPort())
			.withOpenApiPath(value.getOpenApiPath())
			.build();
	}

	private static RuntimeRestOptions copy(RuntimeRestOptions value) {
		return RuntimeRestOptions.builder()
			.withHost(value.getHost())
			.withPort(value.getPort())
			.withOpenApiPath(value.getOpenApiPath())
			.build();
	}

	private static IndexerRuntimeReconcilerOptions copy(
		IndexerRuntimeReconcilerOptions value
	) {
		return IndexerRuntimeReconcilerOptions.builder()
			.withMaxDirtyIndexers(value.getMaxDirtyIndexers())
			.withSafetySyncIntervalMs(value.getSafetySyncIntervalMs())
			.build();
	}

	private static VertxIndexerLifecycleEventBusOptions copy(
		VertxIndexerLifecycleEventBusOptions value
	) {
		return VertxIndexerLifecycleEventBusOptions.builder()
			.withMaxTransportLagMs(value.getMaxTransportLagMs())
			.withSignalCooldownMs(value.getSignalCooldownMs())
			.build();
	}

	private static GatewayRestOptions copy(GatewayRestOptions value) {
		return GatewayRestOptions.builder()
			.withHost(value.getHost())
			.withPort(value.getPort())
			.withOpenApiPath(value.getOpenApiPath())
			.withAdminRestBaseUri(value.getAdminRestBaseUri())
			.withRequestTimeoutMs(value.getRequestTimeoutMs())
			.withApiKey(value.getApiKey())
			.withApiKeyHeader(value.getApiKeyHeader())
			.withRateLimitRequests(value.getRateLimitRequests())
			.withRateLimitWindowMs(value.getRateLimitWindowMs())
			.build();
	}

	private static TargetInvalidationNodeOptions copy(
		TargetInvalidationNodeOptions value
	) {
		return TargetInvalidationNodeOptions.builder()
			.withNamespace(value.getNamespace())
			.withProvider(value.getProvider())
			.withPollIntervalMs(value.getPollIntervalMs())
			.withRetentionFactor(value.getRetentionFactor())
			.withMaxTargets(value.getMaxTargets())
			.build();
	}

	private void addDefaults() {
		services.put(Services.ADMIN, IndexerServiceDeploymentOptions.builder().build());
		services.put(
			Services.ADMIN_REST,
			IndexerServiceDeploymentOptions.builder().withEnabled(false).build()
		);
		services.put(Services.TARGET_ACTION, IndexerServiceDeploymentOptions.builder().build());
		services.put(
			Services.TARGET_ACTION_REST,
			IndexerServiceDeploymentOptions.builder().withEnabled(false).build()
		);
		services.put(Services.RUNTIME, IndexerServiceDeploymentOptions.builder().build());
		services.put(
			Services.RUNTIME_REST,
			IndexerServiceDeploymentOptions.builder().withEnabled(false).build()
		);
		services.put(
			Services.GATEWAY,
			IndexerServiceDeploymentOptions.builder().withEnabled(false).build()
		);
		services.put(
			Services.TARGET_INVALIDATION_REGISTRY,
			IndexerServiceDeploymentOptions.builder().build()
		);
	}

	private static void requireKnownService(String name) {
		if (!Services.ALL.contains(name)) {
			throw new IllegalArgumentException("Unknown indexer node service: " + name);
		}
	}
}
