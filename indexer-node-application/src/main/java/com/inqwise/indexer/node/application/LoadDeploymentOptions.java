package com.inqwise.indexer.node.application;

import io.vertx.core.json.JsonObject;

public record LoadDeploymentOptions(
	boolean enabled
) {
	public static final String CONFIG_KEY = "load";

	public static LoadDeploymentOptions from(JsonObject root) {
		JsonObject config = root == null
			? new JsonObject()
			: root.getJsonObject(CONFIG_KEY, new JsonObject());
		return builder()
			.withEnabled(config.getBoolean("enabled", true))
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private boolean enabled = true;

		private Builder() {
		}

		public Builder withEnabled(boolean value) {
			enabled = value;
			return this;
		}

		public LoadDeploymentOptions build() {
			return new LoadDeploymentOptions(enabled);
		}
	}
}
