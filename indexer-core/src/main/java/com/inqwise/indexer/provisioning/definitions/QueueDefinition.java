package com.inqwise.indexer.provisioning.definitions;

import io.vertx.core.json.JsonObject;

public record QueueDefinition(
	JsonObject settings
) {
	public QueueDefinition {
		settings = settings == null ? new JsonObject() : settings.copy();
	}

	@Override
	public JsonObject settings() {
		return settings.copy();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private JsonObject settings;

		private Builder() {
		}

		public Builder withSettings(JsonObject value) {
			settings = value == null ? null : value.copy();
			return this;
		}

		public QueueDefinition build() {
			return new QueueDefinition(settings);
		}
	}
}
