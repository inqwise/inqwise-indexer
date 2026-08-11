package com.inqwise.indexer.service.admin;

import java.util.Objects;

import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetDefinitionView {
	public static final class Keys {
		public static final String TARGET_NAME = "target_name";
		public static final String PERIOD_STRATEGY = "period_strategy";
		public static final String AUTO_PROVISION_ON_WRITE = "auto_provision_on_write";
		public static final String AUTO_PUBLISH_ON_WRITE = "auto_publish_on_write";

		private Keys() {
		}
	}

	private String targetName;
	private TargetPeriodStrategy periodStrategy;
	private boolean autoProvisionOnWrite;
	private boolean autoPublishOnWrite;

	public AdminTargetDefinitionView() {
	}

	public AdminTargetDefinitionView(JsonObject json) {
		this.targetName = json.getString(Keys.TARGET_NAME);
		String strategy = json.getString(Keys.PERIOD_STRATEGY);
		this.periodStrategy = strategy == null ? null : TargetPeriodStrategy.valueOf(strategy);
		this.autoProvisionOnWrite = json.getBoolean(Keys.AUTO_PROVISION_ON_WRITE, false);
		this.autoPublishOnWrite = json.getBoolean(Keys.AUTO_PUBLISH_ON_WRITE, false);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static AdminTargetDefinitionView from(TargetDefinition definition) {
		Objects.requireNonNull(definition, "definition");
		return builder()
			.withTargetName(definition.targetName())
			.withPeriodStrategy(definition.periodStrategy())
			.withAutoProvisionOnWrite(definition.autoProvisionOnWrite())
			.withAutoPublishOnWrite(definition.autoPublishOnWrite())
			.build();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.TARGET_NAME, targetName)
			.put(Keys.PERIOD_STRATEGY, periodStrategy == null ? null : periodStrategy.name())
			.put(Keys.AUTO_PROVISION_ON_WRITE, autoProvisionOnWrite)
			.put(Keys.AUTO_PUBLISH_ON_WRITE, autoPublishOnWrite);
	}

	public String getTargetName() {
		return targetName;
	}

	public AdminTargetDefinitionView setTargetName(String targetName) {
		this.targetName = targetName;
		return this;
	}

	public TargetPeriodStrategy getPeriodStrategy() {
		return periodStrategy;
	}

	public AdminTargetDefinitionView setPeriodStrategy(TargetPeriodStrategy periodStrategy) {
		this.periodStrategy = periodStrategy;
		return this;
	}

	public boolean isAutoProvisionOnWrite() {
		return autoProvisionOnWrite;
	}

	public AdminTargetDefinitionView setAutoProvisionOnWrite(boolean autoProvisionOnWrite) {
		this.autoProvisionOnWrite = autoProvisionOnWrite;
		return this;
	}

	public boolean isAutoPublishOnWrite() {
		return autoPublishOnWrite;
	}

	public AdminTargetDefinitionView setAutoPublishOnWrite(boolean autoPublishOnWrite) {
		this.autoPublishOnWrite = autoPublishOnWrite;
		return this;
	}

	public static final class Builder {
		private String targetName;
		private TargetPeriodStrategy periodStrategy;
		private boolean autoProvisionOnWrite;
		private boolean autoPublishOnWrite;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withPeriodStrategy(TargetPeriodStrategy value) {
			periodStrategy = value;
			return this;
		}

		public Builder withAutoProvisionOnWrite(boolean value) {
			autoProvisionOnWrite = value;
			return this;
		}

		public Builder withAutoPublishOnWrite(boolean value) {
			autoPublishOnWrite = value;
			return this;
		}

		public AdminTargetDefinitionView build() {
			return new AdminTargetDefinitionView()
				.setTargetName(Objects.requireNonNull(targetName, "targetName"))
				.setPeriodStrategy(Objects.requireNonNull(periodStrategy, "periodStrategy"))
				.setAutoProvisionOnWrite(autoProvisionOnWrite)
				.setAutoPublishOnWrite(autoPublishOnWrite);
		}
	}
}
