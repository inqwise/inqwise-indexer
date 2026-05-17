package com.inqwise.indexer.commands;

import java.util.Objects;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class PublishIndexCommand implements Command {
	public static final String TYPE = "index.publish";

	private final String commandId;
	private final Integer indexerId;
	private final long expectedVersion;

	public PublishIndexCommand(Integer indexerId, long expectedVersion) {
		this(UUID.randomUUID().toString(), indexerId, expectedVersion);
	}

	public PublishIndexCommand(String commandId, Integer indexerId, long expectedVersion) {
		this.commandId = Objects.requireNonNull(commandId, "commandId");
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.expectedVersion = expectedVersion;
	}

	public PublishIndexCommand(JsonObject json) {
		this(
			json.getString("command_id"),
			json.getInteger("indexer_id"),
			json.getLong("expected_version")
		);
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public String getCommandId() {
		return commandId;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public long getExpectedVersion() {
		return expectedVersion;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("command_id", commandId)
			.put("indexer_id", indexerId)
			.put("expected_version", expectedVersion);
	}
}
