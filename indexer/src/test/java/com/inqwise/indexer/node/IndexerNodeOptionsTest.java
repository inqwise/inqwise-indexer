package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.gateway.GatewayRestOptions;
import com.inqwise.indexer.rest.action.TargetActionRestOptions;
import com.inqwise.indexer.rest.admin.AdminRestOptions;
import com.inqwise.indexer.rest.runtime.RuntimeRestOptions;

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
				.put(GatewayRestOptions.Keys.ADMIN_REST_BASE_URI, "http://127.0.0.1:8080")
				.put(GatewayRestOptions.Keys.REQUEST_TIMEOUT_MS, 1000L)));

		assertEquals("0.0.0.0", options.getGatewayOptions().getHost());
		assertEquals(9092, options.getGatewayOptions().getPort());
		assertEquals("http://127.0.0.1:8080", options.getGatewayOptions().getAdminRestBaseUri());
		assertEquals(1000L, options.getGatewayOptions().getRequestTimeoutMs());
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
}
