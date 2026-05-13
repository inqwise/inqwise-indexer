package com.inqwise.indexer.commands;

import java.util.Objects;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class DeactivateIndexerCommand implements Command {
	public static final String TYPE = "indexer.deactivate";

	private final Integer indexerId;
	private final String commandId;

	public DeactivateIndexerCommand(Integer indexerId) {
		this(indexerId, UUID.randomUUID().toString());
	}

	public DeactivateIndexerCommand(Integer indexerId, String commandId) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.commandId = commandId == null ? UUID.randomUUID().toString() : commandId;
	}

	public DeactivateIndexerCommand(JsonObject json) {
		this(json.getInteger("indexer_id"), json.getString("command_id"));
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public String getCommandId() {
		return commandId;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("command_id", commandId);
	}
}
