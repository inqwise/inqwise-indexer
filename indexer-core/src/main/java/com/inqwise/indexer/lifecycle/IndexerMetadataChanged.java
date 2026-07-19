package com.inqwise.indexer.lifecycle;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class IndexerMetadataChanged {
	private final Integer indexerId;
	private final Integer targetId;
	private final String commandType;
	private final long version;

	public IndexerMetadataChanged(
		Integer indexerId,
		Integer targetId,
		String commandType,
		long version
	) {
		this(builder()
			.withIndexerId(indexerId)
			.withTargetId(targetId)
			.withCommandType(commandType)
			.withVersion(version));
	}

	public IndexerMetadataChanged(JsonObject json) {
		this(builder(json));
	}

	private IndexerMetadataChanged(Builder builder) {
		this.indexerId = Objects.requireNonNull(builder.indexerId, "indexerId");
		this.targetId = Objects.requireNonNull(builder.targetId, "targetId");
		this.commandType = Objects.requireNonNull(builder.commandType, "commandType");
		this.version = Objects.requireNonNull(builder.version, "version");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(JsonObject json) {
		Objects.requireNonNull(json, "json");
		return builder()
			.withIndexerId(json.getInteger("indexer_id"))
			.withTargetId(json.getInteger("target_id"))
			.withCommandType(json.getString("command_type"))
			.withVersion(json.getLong("version", 0L));
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public String getCommandType() {
		return commandType;
	}

	public long getVersion() {
		return version;
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("target_id", targetId)
			.put("command_type", commandType)
			.put("version", version);
	}

	public static final class Builder {
		private Integer indexerId;
		private Integer targetId;
		private String commandType;
		private Long version;

		private Builder() {
		}

		public Builder withIndexerId(Integer indexerId) {
			this.indexerId = indexerId;
			return this;
		}

		public Builder withTargetId(Integer targetId) {
			this.targetId = targetId;
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

		public IndexerMetadataChanged build() {
			return new IndexerMetadataChanged(this);
		}
	}
}
