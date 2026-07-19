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

	@Override
	public JsonObject settings() {
		return settings.copy();
	}

	@Override
	public JsonObject mappings() {
		return mappings.copy();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String schemaName;
		private String schemaVersion;
		private JsonObject settings;
		private JsonObject mappings;

		private Builder() {
		}

		public Builder withSchemaName(String value) {
			schemaName = value;
			return this;
		}

		public Builder withSchemaVersion(String value) {
			schemaVersion = value;
			return this;
		}

		public Builder withSettings(JsonObject value) {
			settings = value == null ? null : value.copy();
			return this;
		}

		public Builder withMappings(JsonObject value) {
			mappings = value == null ? null : value.copy();
			return this;
		}

		public IndexDefinition build() {
			return new IndexDefinition(schemaName, schemaVersion, settings, mappings);
		}
	}
}
