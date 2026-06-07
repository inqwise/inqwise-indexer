package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class IndexerNodeOptionsTest {
	@Test
	void defaultsEnableInitialServices() {
		IndexerNodeOptions options = new IndexerNodeOptions();

		assertEquals(1, options.admin().getInstances());
		assertEquals(1, options.targetAction().getInstances());
		assertEquals(1, options.runtime().getInstances());
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
}
