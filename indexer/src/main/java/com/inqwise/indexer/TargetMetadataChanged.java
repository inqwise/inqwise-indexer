package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class TargetMetadataChanged {
	private final Integer targetId;
	private final String commandType;
	private final long version;

	public TargetMetadataChanged(
		Integer targetId,
		String commandType,
		long version
	) {
		this.targetId = Objects.requireNonNull(targetId, "targetId");
		this.commandType = Objects.requireNonNull(commandType, "commandType");
		this.version = version;
	}

	public TargetMetadataChanged(JsonObject json) {
		this(
			json.getInteger("target_id"),
			json.getString("command_type"),
			json.getLong("version", 0L)
		);
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
			.put("target_id", targetId)
			.put("command_type", commandType)
			.put("version", version);
	}
}
