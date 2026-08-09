package com.inqwise.indexer.node.application;

import io.vertx.core.json.JsonObject;

public record HackerNewsActionsDeploymentOptions(boolean enabled, String targetName) {
	public static final String CONFIG_KEY = "hacker_news_actions";

	public static HackerNewsActionsDeploymentOptions from(JsonObject root) {
		JsonObject config = root == null
			? new JsonObject()
			: root.getJsonObject(CONFIG_KEY, new JsonObject());
		return builder()
			.withEnabled(config.getBoolean("enabled", false))
			.withTargetName(config.getString("target_name"))
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private boolean enabled;
		private String targetName;

		private Builder() {
		}

		public Builder withEnabled(boolean value) {
			enabled = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public HackerNewsActionsDeploymentOptions build() {
			if (enabled && (targetName == null || targetName.isBlank())) {
				throw new IllegalArgumentException(
					"hacker_news_actions.target_name is required when enabled"
				);
			}
			return new HackerNewsActionsDeploymentOptions(enabled, targetName);
		}
	}
}
