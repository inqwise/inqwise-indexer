package com.inqwise.indexer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class IndexerMetadataChangedTest {
	@Test
	void serializesOwningTargetId() {
		IndexerMetadataChanged event = new IndexerMetadataChanged(
			10,
			20,
			"indexer.changed",
			3L
		);

		JsonObject json = event.toJson();
		IndexerMetadataChanged parsed = new IndexerMetadataChanged(json);

		assertEquals(10, parsed.getIndexerId());
		assertEquals(20, parsed.getTargetId());
		assertEquals("indexer.changed", parsed.getCommandType());
		assertEquals(3L, parsed.getVersion());
		assertEquals(20, json.getInteger("target_id"));
	}
}
