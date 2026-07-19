package com.inqwise.indexer.provisioning.definitions;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class ProvisioningDefinitionTest {
	@Test
	void isolatesMutableDefinitionJson() {
		JsonObject settings = new JsonObject();
		JsonObject mappings = new JsonObject();
		IndexDefinition index = IndexDefinition.builder()
			.withSchemaName("customers")
			.withSchemaVersion("v1")
			.withSettings(settings)
			.withMappings(mappings)
			.build();
		QueueDefinition queue = QueueDefinition.builder()
			.withSettings(settings)
			.build();

		settings.put("input", true);
		mappings.put("input", true);
		index.settings().put("output", true);
		index.mappings().put("output", true);
		queue.settings().put("output", true);

		assertFalse(index.settings().containsKey("input"));
		assertFalse(index.settings().containsKey("output"));
		assertFalse(index.mappings().containsKey("input"));
		assertFalse(index.mappings().containsKey("output"));
		assertFalse(queue.settings().containsKey("input"));
		assertFalse(queue.settings().containsKey("output"));
	}
}
