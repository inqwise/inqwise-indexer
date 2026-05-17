package com.inqwise.indexer.commands;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.inqwise.indexer.IndexerActionItem;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SubmitIndexActionsCommand implements Command {
	public static final String TYPE = "indexer.actions.submit";

	private final String commandId;
	private final String batchId;
	private final List<IndexerActionItem> actions;

	public SubmitIndexActionsCommand(
		List<IndexerActionItem> actions
	) {
		this(UUID.randomUUID().toString(), UUID.randomUUID().toString(), actions);
	}

	public SubmitIndexActionsCommand(
		String commandId,
		String batchId,
		List<IndexerActionItem> actions
	) {
		this.commandId = Objects.requireNonNull(commandId, "commandId");
		this.batchId = Objects.requireNonNull(batchId, "batchId");
		this.actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
	}

	public SubmitIndexActionsCommand(JsonObject json) {
		this(
			json.getString("command_id"),
			json.getString("batch_id"),
			json.getJsonArray("actions", new JsonArray()).stream()
				.map(JsonObject.class::cast)
				.map(IndexerActionItem::fromJson)
				.toList()
		);
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public String getCommandId() {
		return commandId;
	}

	public String getBatchId() {
		return batchId;
	}

	public List<IndexerActionItem> getActions() {
		return actions;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("command_id", commandId)
			.put("batch_id", batchId)
			.put("actions", new JsonArray(actions.stream()
				.map(IndexerActionItem::toJson)
				.toList()));
	}
}
