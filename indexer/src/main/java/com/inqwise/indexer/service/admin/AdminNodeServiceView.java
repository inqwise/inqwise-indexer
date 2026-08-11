package com.inqwise.indexer.service.admin;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminNodeServiceView {
	public static final class Keys {
		public static final String NAME = "name";
		public static final String GROUP = "group";
		public static final String ENABLED = "enabled";
		public static final String CONFIGURED_INSTANCES = "configured_instances";
		public static final String DEPLOYED_INSTANCES = "deployed_instances";

		private Keys() {
		}
	}

	private String name;
	private String group;
	private boolean enabled;
	private int configuredInstances;
	private int deployedInstances;

	public AdminNodeServiceView() {
	}

	public AdminNodeServiceView(JsonObject json) {
		this.name = json.getString(Keys.NAME);
		this.group = json.getString(Keys.GROUP);
		this.enabled = json.getBoolean(Keys.ENABLED, false);
		this.configuredInstances = json.getInteger(Keys.CONFIGURED_INSTANCES, 0);
		this.deployedInstances = json.getInteger(Keys.DEPLOYED_INSTANCES, 0);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.NAME, name)
			.put(Keys.GROUP, group)
			.put(Keys.ENABLED, enabled)
			.put(Keys.CONFIGURED_INSTANCES, configuredInstances)
			.put(Keys.DEPLOYED_INSTANCES, deployedInstances);
	}

	public static final class Builder {
		private String name;
		private String group;
		private Boolean enabled;
		private Integer configuredInstances;
		private Integer deployedInstances;

		private Builder() {
		}

		public Builder withName(String value) {
			name = value;
			return this;
		}

		public Builder withGroup(String value) {
			group = value;
			return this;
		}

		public Builder withEnabled(boolean value) {
			enabled = value;
			return this;
		}

		public Builder withConfiguredInstances(int value) {
			configuredInstances = value;
			return this;
		}

		public Builder withDeployedInstances(int value) {
			deployedInstances = value;
			return this;
		}

		public AdminNodeServiceView build() {
			AdminNodeServiceView view = new AdminNodeServiceView();
			view.name = Objects.requireNonNull(name, "name");
			view.group = Objects.requireNonNull(group, "group");
			view.enabled = Objects.requireNonNull(enabled, "enabled");
			view.configuredInstances = Objects.requireNonNull(
				configuredInstances,
				"configuredInstances"
			);
			view.deployedInstances = Objects.requireNonNull(
				deployedInstances,
				"deployedInstances"
			);
			return view;
		}
	}
}
