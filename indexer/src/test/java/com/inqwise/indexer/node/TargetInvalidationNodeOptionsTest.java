package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class TargetInvalidationNodeOptionsTest {
	@Test
	void roundTripsProviderAndRegistryBounds() {
		TargetInvalidationNodeOptions options = new TargetInvalidationNodeOptions(new JsonObject()
			.put(TargetInvalidationNodeOptions.Keys.NAMESPACE, "production")
			.put(
				TargetInvalidationNodeOptions.Keys.PROVIDER,
				TargetInvalidationNodeOptions.Provider.VERTX_SHARED_DATA.name()
			)
			.put(TargetInvalidationNodeOptions.Keys.POLL_INTERVAL_MS, 5_000L)
			.put(TargetInvalidationNodeOptions.Keys.RETENTION_FACTOR, 4)
			.put(TargetInvalidationNodeOptions.Keys.MAX_TARGETS, 500));

		assertEquals("production", options.getNamespace());
		assertEquals(
			TargetInvalidationNodeOptions.Provider.VERTX_SHARED_DATA,
			options.getProvider()
		);
		assertEquals(20_000L, options.registryOptions().ttl().toMillis());
		assertEquals(500, options.registryOptions().maxTargets());
		assertEquals(options.toJson(), new TargetInvalidationNodeOptions(options.toJson()).toJson());
	}

	@Test
	void rejectsInvalidRegistryBounds() {
		assertThrows(IllegalArgumentException.class, () -> new TargetInvalidationNodeOptions()
			.setPollIntervalMs(0L));
		assertThrows(IllegalArgumentException.class, () -> new TargetInvalidationNodeOptions()
			.setRetentionFactor(1));
		assertThrows(IllegalArgumentException.class, () -> new TargetInvalidationNodeOptions()
			.setMaxTargets(0));
	}
}
