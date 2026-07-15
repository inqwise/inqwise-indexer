package com.inqwise.indexer.provisioning.definitions;

import io.vertx.core.json.JsonObject;

public record QueueDefinition(
	JsonObject settings
) {
	public QueueDefinition {
		settings = settings == null ? new JsonObject() : settings.copy();
	}
}
