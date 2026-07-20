package com.inqwise.indexer.node;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerServiceDeploymentOptions {
	public static final class Keys {
		public static final String ENABLED = "enabled";
		public static final String INSTANCES = "instances";

		private Keys() {
		}
	}

	private boolean enabled;
	private int instances;

	public IndexerServiceDeploymentOptions() {
		this.enabled = true;
		this.instances = 1;
	}

	public IndexerServiceDeploymentOptions(JsonObject json) {
		this.enabled = json.getBoolean(Keys.ENABLED, true);
		this.instances = json.getInteger(Keys.INSTANCES, 1);
		validate();
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.ENABLED, enabled)
			.put(Keys.INSTANCES, instances);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public IndexerServiceDeploymentOptions setEnabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}

	public int getInstances() {
		return instances;
	}

	public IndexerServiceDeploymentOptions setInstances(int instances) {
		this.instances = instances;
		validate();
		return this;
	}

	void validate() {
		if (instances < 1) {
			throw new IllegalArgumentException("Service instances must be at least 1");
		}
	}

	public static final class Builder {
		private boolean enabled = true;
		private int instances = 1;

		private Builder() {
		}

		public Builder withEnabled(boolean value) {
			enabled = value;
			return this;
		}

		public Builder withInstances(int value) {
			instances = value;
			return this;
		}

		public IndexerServiceDeploymentOptions build() {
			return new IndexerServiceDeploymentOptions()
				.setEnabled(enabled)
				.setInstances(instances);
		}
	}
}
