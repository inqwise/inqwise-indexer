package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBusConfig;
import com.inqwise.indexer.runtime.IndexerRuntimeReconcilerOptions;
import com.inqwise.indexer.lifecycle.VertxIndexerLifecycleEventBusOptions;
import com.inqwise.indexer.gateway.GatewayRestOptions;
import com.inqwise.indexer.rest.action.TargetActionRestOptions;
import com.inqwise.indexer.rest.admin.AdminRestOptions;
import com.inqwise.indexer.rest.runtime.RuntimeRestOptions;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class IndexerNodeOptionsTest {
	@Test
	void defaultsEnableInitialServices() {
		IndexerNodeOptions options = new IndexerNodeOptions();

		assertEquals(1, options.admin().getInstances());
		assertFalse(options.adminRest().isEnabled());
		assertEquals(1, options.adminRest().getInstances());
		assertEquals(1, options.targetAction().getInstances());
		assertFalse(options.targetActionRest().isEnabled());
		assertEquals(1, options.targetActionRest().getInstances());
		assertEquals(1, options.runtime().getInstances());
		assertFalse(options.gateway().isEnabled());
		assertEquals(1, options.gateway().getInstances());
		assertFalse(options.runtimeRest().isEnabled());
		assertEquals(1, options.runtimeRest().getInstances());
		assertFalse(options.healthRest().isEnabled());
		assertEquals(1, options.healthRest().getInstances());
		assertEquals(
			NodeHealthRestOptions.DEFAULT_PORT,
			options.getHealthRestOptions().getPort()
		);
		assertEquals(1, options.targetInvalidationRegistry().getInstances());
		assertEquals(
			TargetInvalidationNodeOptions.Provider.VERTX_SHARED_DATA,
			options.getTargetInvalidationOptions().getProvider()
		);
		assertEquals(
			IndexerRuntimeReconcilerOptions.DEFAULT_MAX_DIRTY_INDEXERS,
			options.getRuntimeReconcilerOptions().getMaxDirtyIndexers()
		);
		assertEquals(
			IndexerRuntimeReconcilerOptions.DEFAULT_SAFETY_SYNC_INTERVAL_MS,
			options.getRuntimeReconcilerOptions().getSafetySyncIntervalMs()
		);
		assertEquals(
			IndexerNodeOptions.LifecycleEvents.DEFAULT_NAMESPACE,
			options.getLifecycleEventBusConfig().namespace()
		);
		assertEquals(
			VertxIndexerLifecycleEventBusOptions.DEFAULT_MAX_TRANSPORT_LAG_MS,
			options.getLifecycleEventBusOptions().getMaxTransportLagMs()
		);
	}

	@Test
	void builderCopiesMutableNestedOptions() {
		AdminRestOptions adminRestOptions = AdminRestOptions.builder()
			.withPort(9090)
			.build();
		IndexerServiceDeploymentOptions adminDeployment =
			IndexerServiceDeploymentOptions.builder()
				.withEnabled(false)
				.build();
		IndexerNodeOptions.Builder builder = IndexerNodeOptions.builder()
			.withAdminRestOptions(adminRestOptions)
			.withService(IndexerNodeOptions.Services.ADMIN, adminDeployment);

		adminRestOptions.setPort(9091);
		adminDeployment.setEnabled(true);
		IndexerNodeOptions first = builder.build();
		assertEquals(9090, first.getAdminRestOptions().getPort());
		assertFalse(first.admin().isEnabled());

		first.getAdminRestOptions().setPort(9092);
		first.admin().setEnabled(true);
		IndexerNodeOptions second = builder.build();
		assertEquals(9090, second.getAdminRestOptions().getPort());
		assertFalse(second.admin().isEnabled());
	}

	@Test
	void builderComposesEveryNestedOptionGroup() {
		IndexerNodeOptions options = IndexerNodeOptions.builder()
			.withAdminRestOptions(AdminRestOptions.builder().withPort(9001).build())
			.withTargetActionRestOptions(
				TargetActionRestOptions.builder().withPort(9002).build()
			)
			.withRuntimeRestOptions(RuntimeRestOptions.builder().withPort(9003).build())
			.withHealthRestOptions(NodeHealthRestOptions.builder().withPort(9005).build())
			.withRuntimeReconcilerOptions(
				IndexerRuntimeReconcilerOptions.builder()
					.withMaxDirtyIndexers(25)
					.withSafetySyncIntervalMs(1_000L)
					.build()
			)
			.withLifecycleEventBusConfig(
				IndexerLifecycleEventBusConfig.builder()
					.withNamespace("production")
					.build()
			)
			.withLifecycleEventBusOptions(
				VertxIndexerLifecycleEventBusOptions.builder()
					.withMaxTransportLagMs(500L)
					.withSignalCooldownMs(2_000L)
					.build()
			)
			.withGatewayOptions(
				GatewayRestOptions.builder()
					.withPort(9004)
					.withApiKey("secret")
					.build()
			)
			.withTargetInvalidationOptions(
				TargetInvalidationNodeOptions.builder()
					.withNamespace("production")
					.withMaxTargets(500)
					.build()
			)
			.build();

		assertEquals(9001, options.getAdminRestOptions().getPort());
		assertEquals(9002, options.getTargetActionRestOptions().getPort());
		assertEquals(9003, options.getRuntimeRestOptions().getPort());
		assertEquals(9005, options.getHealthRestOptions().getPort());
		assertEquals(25, options.getRuntimeReconcilerOptions().getMaxDirtyIndexers());
		assertEquals("production", options.getLifecycleEventBusConfig().namespace());
		assertEquals(500L, options.getLifecycleEventBusOptions().getMaxTransportLagMs());
		assertEquals(9004, options.getGatewayOptions().getPort());
		assertEquals("secret", options.getGatewayOptions().getApiKey());
		assertEquals("production", options.getTargetInvalidationOptions().getNamespace());
		assertEquals(500, options.getTargetInvalidationOptions().getMaxTargets());
	}

	@Test
	void rejectsMultipleInMemoryInvalidationServiceInstances() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions()
				.setTargetInvalidationOptions(new TargetInvalidationNodeOptions()
					.setProvider(TargetInvalidationNodeOptions.Provider.IN_MEMORY))
				.setService(
					IndexerNodeOptions.Services.TARGET_INVALIDATION_REGISTRY,
					new IndexerServiceDeploymentOptions().setInstances(2)
				)
		);

		assertEquals(
			"In-memory target invalidation service supports exactly one instance",
			error.getMessage()
		);
	}

	@Test
	void readsLifecycleEventOptionsFromJson() {
		IndexerNodeOptions options = new IndexerNodeOptions(new JsonObject()
			.put(IndexerNodeOptions.Keys.LIFECYCLE_EVENTS, new JsonObject()
				.put(IndexerNodeOptions.LifecycleEvents.NAMESPACE, "production")
				.put(VertxIndexerLifecycleEventBusOptions.Keys.MAX_TRANSPORT_LAG_MS, 500L)
				.put(VertxIndexerLifecycleEventBusOptions.Keys.SIGNAL_COOLDOWN_MS, 2_000L)));

		assertEquals("production", options.getLifecycleEventBusConfig().namespace());
		assertEquals(500L, options.getLifecycleEventBusOptions().getMaxTransportLagMs());
		assertEquals(2_000L, options.getLifecycleEventBusOptions().getSignalCooldownMs());
		JsonObject serialized = options.toJson().getJsonObject(
			IndexerNodeOptions.Keys.LIFECYCLE_EVENTS
		);
		assertEquals("production", serialized.getString(
			IndexerNodeOptions.LifecycleEvents.NAMESPACE
		));
		assertEquals(500L, serialized.getLong(
			VertxIndexerLifecycleEventBusOptions.Keys.MAX_TRANSPORT_LAG_MS
		));
	}

	@Test
	void rejectsInvalidLifecycleEventOptions() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.LIFECYCLE_EVENTS, new JsonObject()
					.put(IndexerNodeOptions.LifecycleEvents.NAMESPACE, " ")))
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.LIFECYCLE_EVENTS, new JsonObject()
					.put(
						VertxIndexerLifecycleEventBusOptions.Keys.SIGNAL_COOLDOWN_MS,
						0L
					)))
		);
	}

	@Test
	void readsRuntimeReconcilerOptionsFromJson() {
		IndexerNodeOptions options = new IndexerNodeOptions(new JsonObject()
			.put(IndexerNodeOptions.Keys.RUNTIME_RECONCILER, new JsonObject()
				.put(IndexerRuntimeReconcilerOptions.Keys.MAX_DIRTY_INDEXERS, 25)
				.put(IndexerRuntimeReconcilerOptions.Keys.SAFETY_SYNC_INTERVAL_MS, 1_000L)));

		assertEquals(25, options.getRuntimeReconcilerOptions().getMaxDirtyIndexers());
		assertEquals(1_000L, options.getRuntimeReconcilerOptions().getSafetySyncIntervalMs());
		assertEquals(
			25,
			options.toJson()
				.getJsonObject(IndexerNodeOptions.Keys.RUNTIME_RECONCILER)
				.getInteger(IndexerRuntimeReconcilerOptions.Keys.MAX_DIRTY_INDEXERS)
		);
		assertEquals(
			1_000L,
			options.toJson()
				.getJsonObject(IndexerNodeOptions.Keys.RUNTIME_RECONCILER)
				.getLong(IndexerRuntimeReconcilerOptions.Keys.SAFETY_SYNC_INTERVAL_MS)
		);
	}

	@Test
	void rejectsNonPositiveRuntimeReconcilerCapacity() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.RUNTIME_RECONCILER, new JsonObject()
					.put(IndexerRuntimeReconcilerOptions.Keys.MAX_DIRTY_INDEXERS, 0)))
		);

		assertEquals("maxDirtyIndexers must be greater than zero", error.getMessage());
	}

	@Test
	void rejectsNonPositiveRuntimeReconcilerSafetyInterval() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.RUNTIME_RECONCILER, new JsonObject()
					.put(IndexerRuntimeReconcilerOptions.Keys.SAFETY_SYNC_INTERVAL_MS, 0L)))
		);

		assertEquals(
			"safetySyncIntervalMs must be greater than zero",
			error.getMessage()
		);
	}

	@Test
	void rejectsUnknownService() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.SERVICES, new JsonObject()
					.put("unknown", new JsonObject())))
		);

		assertEquals("Unknown indexer node service: unknown", error.getMessage());
	}

	@Test
	void rejectsZeroInstances() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.SERVICES, new JsonObject()
					.put(IndexerNodeOptions.Services.TARGET_ACTION, new JsonObject()
						.put(IndexerServiceDeploymentOptions.Keys.INSTANCES, 0))))
		);

		assertEquals("Service instances must be at least 1", error.getMessage());
	}

	@Test
	void rejectsMultipleRuntimeInstancesWhenEnabled() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.SERVICES, new JsonObject()
					.put(IndexerNodeOptions.Services.RUNTIME, new JsonObject()
						.put(IndexerServiceDeploymentOptions.Keys.INSTANCES, 2))))
		);

		assertEquals("Runtime service must be deployed with exactly one instance", error.getMessage());
	}

	@Test
	void allowsDisabledRuntimeWithConfiguredInstances() {
		IndexerNodeOptions options = new IndexerNodeOptions(new JsonObject()
			.put(IndexerNodeOptions.Keys.SERVICES, new JsonObject()
				.put(IndexerNodeOptions.Services.RUNTIME, new JsonObject()
					.put(IndexerServiceDeploymentOptions.Keys.ENABLED, false)
					.put(IndexerServiceDeploymentOptions.Keys.INSTANCES, 2))));

		assertFalse(options.runtime().isEnabled());
		assertEquals(2, options.runtime().getInstances());
	}

	@Test
	void rejectsMultipleAdminRestInstancesWhenEnabled() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.SERVICES, new JsonObject()
					.put(IndexerNodeOptions.Services.ADMIN_REST, new JsonObject()
						.put(IndexerServiceDeploymentOptions.Keys.INSTANCES, 2))))
		);

		assertEquals("Admin REST service must be deployed with exactly one instance", error.getMessage());
	}

	@Test
	void rejectsMultipleTargetActionRestInstancesWhenEnabled() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.SERVICES, new JsonObject()
					.put(IndexerNodeOptions.Services.TARGET_ACTION_REST, new JsonObject()
						.put(IndexerServiceDeploymentOptions.Keys.INSTANCES, 2))))
		);

		assertEquals(
			"Target action REST service must be deployed with exactly one instance",
			error.getMessage()
		);
	}

	@Test
	void rejectsMultipleGatewayInstancesWhenEnabled() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.SERVICES, new JsonObject()
					.put(IndexerNodeOptions.Services.GATEWAY, new JsonObject()
						.put(IndexerServiceDeploymentOptions.Keys.INSTANCES, 2))))
		);

		assertEquals("Gateway service must be deployed with exactly one instance", error.getMessage());
	}

	@Test
	void rejectsMultipleRuntimeRestInstancesWhenEnabled() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.SERVICES, new JsonObject()
					.put(IndexerNodeOptions.Services.RUNTIME_REST, new JsonObject()
						.put(IndexerServiceDeploymentOptions.Keys.INSTANCES, 2))))
		);

		assertEquals("Runtime REST service must be deployed with exactly one instance", error.getMessage());
	}

	@Test
	void rejectsMultipleHealthRestInstancesWhenEnabled() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerNodeOptions(new JsonObject()
				.put(IndexerNodeOptions.Keys.SERVICES, new JsonObject()
					.put(IndexerNodeOptions.Services.HEALTH_REST, new JsonObject()
						.put(IndexerServiceDeploymentOptions.Keys.INSTANCES, 2))))
		);

		assertEquals(
			"Health REST service must be deployed with exactly one instance",
			error.getMessage()
		);
	}

	@Test
	void readsAdminRestOptionsFromJson() {
		IndexerNodeOptions options = new IndexerNodeOptions(new JsonObject()
			.put(IndexerNodeOptions.Keys.ADMIN_REST, new JsonObject()
				.put(AdminRestOptions.Keys.HOST, "0.0.0.0")
				.put(AdminRestOptions.Keys.PORT, 9090)));

		assertEquals("0.0.0.0", options.getAdminRestOptions().getHost());
		assertEquals(9090, options.getAdminRestOptions().getPort());
	}

	@Test
	void readsTargetActionRestOptionsFromJson() {
		IndexerNodeOptions options = new IndexerNodeOptions(new JsonObject()
			.put(IndexerNodeOptions.Keys.TARGET_ACTION_REST, new JsonObject()
				.put(TargetActionRestOptions.Keys.HOST, "0.0.0.0")
				.put(TargetActionRestOptions.Keys.PORT, 9091)));

		assertEquals("0.0.0.0", options.getTargetActionRestOptions().getHost());
		assertEquals(9091, options.getTargetActionRestOptions().getPort());
	}

	@Test
	void readsGatewayOptionsFromJson() {
		IndexerNodeOptions options = new IndexerNodeOptions(new JsonObject()
			.put(IndexerNodeOptions.Keys.GATEWAY, new JsonObject()
				.put(GatewayRestOptions.Keys.HOST, "0.0.0.0")
				.put(GatewayRestOptions.Keys.PORT, 9092)
				.put(GatewayRestOptions.Keys.OPEN_API_PATH, "openapi/custom-gateway.yaml")
				.put(GatewayRestOptions.Keys.ADMIN_REST_BASE_URI, "http://127.0.0.1:8080")
				.put(GatewayRestOptions.Keys.REQUEST_TIMEOUT_MS, 1000L)
				.put(GatewayRestOptions.Keys.API_KEY, "secret")
				.put(GatewayRestOptions.Keys.API_KEY_HEADER, "x-indexer-key")
				.put(GatewayRestOptions.Keys.RATE_LIMIT_REQUESTS, 10)
				.put(GatewayRestOptions.Keys.RATE_LIMIT_WINDOW_MS, 1000L)));

		assertEquals("0.0.0.0", options.getGatewayOptions().getHost());
		assertEquals(9092, options.getGatewayOptions().getPort());
		assertEquals("openapi/custom-gateway.yaml", options.getGatewayOptions().getOpenApiPath());
		assertEquals("http://127.0.0.1:8080", options.getGatewayOptions().getAdminRestBaseUri());
		assertEquals(1000L, options.getGatewayOptions().getRequestTimeoutMs());
		assertEquals("secret", options.getGatewayOptions().getApiKey());
		assertEquals("x-indexer-key", options.getGatewayOptions().getApiKeyHeader());
		assertEquals(10, options.getGatewayOptions().getRateLimitRequests());
		assertEquals(1000L, options.getGatewayOptions().getRateLimitWindowMs());
	}

	@Test
	void readsRuntimeRestOptionsFromJson() {
		IndexerNodeOptions options = new IndexerNodeOptions(new JsonObject()
			.put(IndexerNodeOptions.Keys.RUNTIME_REST, new JsonObject()
				.put(RuntimeRestOptions.Keys.HOST, "0.0.0.0")
				.put(RuntimeRestOptions.Keys.PORT, 9093)));

		assertEquals("0.0.0.0", options.getRuntimeRestOptions().getHost());
		assertEquals(9093, options.getRuntimeRestOptions().getPort());
	}

	@Test
	void readsHealthRestOptionsFromJson() {
		IndexerNodeOptions options = new IndexerNodeOptions(new JsonObject()
			.put(IndexerNodeOptions.Keys.HEALTH_REST, new JsonObject()
				.put(NodeHealthRestOptions.Keys.HOST, "0.0.0.0")
				.put(NodeHealthRestOptions.Keys.PORT, 9094)));

		assertEquals("0.0.0.0", options.getHealthRestOptions().getHost());
		assertEquals(9094, options.getHealthRestOptions().getPort());
		assertEquals(
			9094,
			options.toJson()
				.getJsonObject(IndexerNodeOptions.Keys.HEALTH_REST)
				.getInteger(NodeHealthRestOptions.Keys.PORT)
		);
	}

	@Test
	void readsAndSerializesTargetDefinitions() {
		IndexerNodeOptions options = new IndexerNodeOptions(new JsonObject()
			.put(IndexerNodeOptions.Keys.TARGET_DEFINITIONS, new JsonArray()
				.add(new JsonObject()
					.put(IndexerNodeOptions.TargetDefinitions.TARGET_NAME, "customers")
					.put(
						IndexerNodeOptions.TargetDefinitions.PERIOD_STRATEGY,
						TargetPeriodStrategy.MONTHLY.name()
					)
					.put(
						IndexerNodeOptions.TargetDefinitions.AUTO_PROVISION_ON_WRITE,
						true
					))));

		assertEquals(1, options.targetDefinitions().size());
		TargetDefinition definition = options.targetDefinitions().get(0);
		assertEquals("customers", definition.targetName());
		assertEquals(TargetPeriodStrategy.MONTHLY, definition.periodStrategy());
		assertTrue(definition.autoProvisionOnWrite());
		assertEquals(
			"customers",
			options.toJson()
				.getJsonArray(IndexerNodeOptions.Keys.TARGET_DEFINITIONS)
				.getJsonObject(0)
				.getString(IndexerNodeOptions.TargetDefinitions.TARGET_NAME)
		);
	}

	@Test
	void builderCopiesTargetDefinitions() {
		List<TargetDefinition> definitions = List.of(TargetDefinition.builder()
			.withTargetName("customers")
			.withPeriodStrategy(TargetPeriodStrategy.MONTHLY)
			.withAutoProvisionOnWrite(true)
			.build());

		IndexerNodeOptions options = IndexerNodeOptions.builder()
			.withTargetDefinitions(definitions)
			.build();

		assertEquals(definitions, options.targetDefinitions());
		assertThrows(
			UnsupportedOperationException.class,
			() -> options.targetDefinitions().add(definitions.get(0))
		);
	}

	@Test
	void rejectsDuplicateTargetDefinitions() {
		TargetDefinition definition = TargetDefinition.builder()
			.withTargetName("customers")
			.build();

		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> IndexerNodeOptions.builder()
				.withTargetDefinitions(List.of(definition, definition))
				.build()
		);

		assertEquals("Duplicate target definition: customers", error.getMessage());
	}
}
