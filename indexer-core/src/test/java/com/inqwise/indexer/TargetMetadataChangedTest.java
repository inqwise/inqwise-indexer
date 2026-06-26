package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class TargetMetadataChangedTest {
	@Test
	void serializesTargetEnvelope() {
		TargetMetadataChanged event = new TargetMetadataChanged(
			10,
			"customers",
			"2026-05",
			"target.changed",
			3L
		);

		JsonObject json = event.toJson();
		TargetMetadataChanged parsed = new TargetMetadataChanged(json);

		assertEquals(10, parsed.getTargetId());
		assertEquals("customers", parsed.getTargetName());
		assertEquals("2026-05", parsed.getPeriodKey());
		assertEquals("target.changed", parsed.getCommandType());
		assertEquals(3L, parsed.getVersion());
		assertEquals("customers", json.getString("target_name"));
		assertEquals("2026-05", json.getString("period_key"));
	}
}
