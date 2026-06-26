package com.inqwise.indexer;

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
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.targetId = Objects.requireNonNull(targetId, "targetId");
		this.commandType = Objects.requireNonNull(commandType, "commandType");
		this.version = version;
	}

	public IndexerMetadataChanged(JsonObject json) {
		this(
			json.getInteger("indexer_id"),
			json.getInteger("target_id"),
			json.getString("command_type"),
			json.getLong("version", 0L)
		);
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
}
