package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class NodeHealthRestOptionsTest {
	@Test
	void buildsAndSerializesOptions() {
		NodeHealthRestOptions options = NodeHealthRestOptions.builder()
			.withHost("0.0.0.0")
			.withPort(9084)
			.build();

		assertEquals("0.0.0.0", options.getHost());
		assertEquals(9084, options.getPort());
		assertEquals("0.0.0.0", options.toJson().getString(NodeHealthRestOptions.Keys.HOST));
		assertEquals(9084, options.toJson().getInteger(NodeHealthRestOptions.Keys.PORT));
	}

	@Test
	void readsJsonAndAppliesDefaults() {
		NodeHealthRestOptions configured = new NodeHealthRestOptions(new JsonObject()
			.put(NodeHealthRestOptions.Keys.HOST, "0.0.0.0")
			.put(NodeHealthRestOptions.Keys.PORT, 9084));
		NodeHealthRestOptions defaults = new NodeHealthRestOptions(new JsonObject());

		assertEquals("0.0.0.0", configured.getHost());
		assertEquals(9084, configured.getPort());
		assertEquals(NodeHealthRestOptions.DEFAULT_HOST, defaults.getHost());
		assertEquals(NodeHealthRestOptions.DEFAULT_PORT, defaults.getPort());
	}

	@Test
	void rejectsInvalidOptions() {
		assertThrows(
			IllegalArgumentException.class,
			() -> NodeHealthRestOptions.builder().withHost(" ").build()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> NodeHealthRestOptions.builder().withPort(65536).build()
		);
	}
}
