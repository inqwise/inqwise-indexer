package com.inqwise.indexer.lifecycle;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class TargetMetadataChanged {
	private final Integer targetId;
	private final String targetName;
	private final String periodKey;
	private final String commandType;
	private final long version;

	public TargetMetadataChanged(
		Integer targetId,
		String targetName,
		String periodKey,
		String commandType,
		long version
	) {
		this(builder()
			.withTargetId(targetId)
			.withTargetName(targetName)
			.withPeriodKey(periodKey)
			.withCommandType(commandType)
			.withVersion(version));
	}

	public TargetMetadataChanged(JsonObject json) {
		this(builder(json));
	}

	private TargetMetadataChanged(Builder builder) {
		this.targetId = Objects.requireNonNull(builder.targetId, "targetId");
		this.targetName = Objects.requireNonNull(builder.targetName, "targetName");
		this.periodKey = builder.periodKey;
		this.commandType = Objects.requireNonNull(builder.commandType, "commandType");
		this.version = Objects.requireNonNull(builder.version, "version");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(JsonObject json) {
		Objects.requireNonNull(json, "json");
		return builder()
			.withTargetId(json.getInteger("target_id"))
			.withTargetName(json.getString("target_name"))
			.withPeriodKey(json.getString("period_key"))
			.withCommandType(json.getString("command_type"))
			.withVersion(json.getLong("version", 0L));
	}

	public Integer getTargetId() {
		return targetId;
	}

	public String getTargetName() {
		return targetName;
	}

	public String getPeriodKey() {
		return periodKey;
	}

	public String getCommandType() {
		return commandType;
	}

	public long getVersion() {
		return version;
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("target_id", targetId)
			.put("target_name", targetName)
			.put("period_key", periodKey)
			.put("command_type", commandType)
			.put("version", version);
	}

	public static final class Builder {
		private Integer targetId;
		private String targetName;
		private String periodKey;
		private String commandType;
		private Long version;

		private Builder() {
		}

		public Builder withTargetId(Integer targetId) {
			this.targetId = targetId;
			return this;
		}

		public Builder withTargetName(String targetName) {
			this.targetName = targetName;
			return this;
		}

		public Builder withPeriodKey(String periodKey) {
			this.periodKey = periodKey;
			return this;
		}

		public Builder withCommandType(String commandType) {
			this.commandType = commandType;
			return this;
		}

		public Builder withVersion(long version) {
			this.version = version;
			return this;
		}

		public TargetMetadataChanged build() {
			return new TargetMetadataChanged(this);
		}
	}
}
