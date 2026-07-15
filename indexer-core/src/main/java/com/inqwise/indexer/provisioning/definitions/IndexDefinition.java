package com.inqwise.indexer.provisioning.definitions;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record IndexDefinition(
	String schemaName,
	String schemaVersion,
	JsonObject settings,
	JsonObject mappings
) {
	public IndexDefinition {
		schemaName = Objects.requireNonNull(schemaName, "schemaName");
		schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
		settings = settings == null ? new JsonObject() : settings.copy();
		mappings = mappings == null ? new JsonObject() : mappings.copy();
	}
}
