package com.inqwise.indexer.commands;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerActionType;
import com.inqwise.indexer.PutDocumentActionItem;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SubmitIndexActionsCommand implements Command {
	public static final String TYPE = "indexer.actions.submit";
	public static final int MAX_ACTIONS = 1000;
	public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;

	private final String commandId;
	private final String targetUid;
	private final String targetName;
	private final Instant timestamp;
	private final List<IndexerActionItem> actions;

	public SubmitIndexActionsCommand(
		List<IndexerActionItem> actions
	) {
		this(UUID.randomUUID().toString(), actions);
	}

	public SubmitIndexActionsCommand(
		String commandId,
		List<IndexerActionItem> actions
	) {
		this(commandId, null, null, null, actions);
	}

	public SubmitIndexActionsCommand(
		String targetUid,
		String targetName,
		Instant timestamp,
		List<IndexerActionItem> actions
	) {
		this(UUID.randomUUID().toString(), targetUid, targetName, timestamp, actions);
	}

	public SubmitIndexActionsCommand(
		String commandId,
		String targetUid,
		String targetName,
		Instant timestamp,
		List<IndexerActionItem> actions
	) {
		this.commandId = Objects.requireNonNull(commandId, "commandId");
		this.targetUid = targetUid;
		this.targetName = targetName;
		this.timestamp = timestamp;
		this.actions = validateActions(actions);
	}

	public SubmitIndexActionsCommand(JsonObject json) {
		this(
			json.getString("command_id"),
			json.getString("target_uid"),
			json.getString("target_name"),
			json.getString("timestamp") == null ? null : Instant.parse(json.getString("timestamp")),
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

	public String getTargetUid() {
		return targetUid;
	}

	public String getTargetName() {
		return targetName;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public List<IndexerActionItem> getActions() {
		return actions;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("command_id", commandId)
			.put("target_uid", targetUid)
			.put("target_name", targetName)
			.put("timestamp", timestamp == null ? null : timestamp.toString())
			.put("actions", new JsonArray(actions.stream()
				.map(IndexerActionItem::toJson)
				.toList()));
	}

	private List<IndexerActionItem> validateActions(List<IndexerActionItem> actions) {
		List<IndexerActionItem> copy = List.copyOf(Objects.requireNonNull(actions, "actions"));
		if (copy.isEmpty()) {
			throw new IllegalArgumentException("No actions submitted");
		}

		if (copy.size() > MAX_ACTIONS) {
			throw new IllegalArgumentException("Too many actions submitted: " + copy.size());
		}

		boolean hasTargetEnvelope = targetUid != null || targetName != null;
		for (IndexerActionItem action : copy) {
			validateRouteMode(action, hasTargetEnvelope);
			if (action.getActionType() == IndexerActionType.PUT_DOCUMENT) {
				PutDocumentActionItem put = (PutDocumentActionItem) action;
				int documentBytes = put.getDocument().encode().getBytes(StandardCharsets.UTF_8).length;
				if (documentBytes > MAX_DOCUMENT_BYTES) {
					throw new IllegalArgumentException("Document is too large: " + documentBytes);
				}
			}
		}

		return copy;
	}

	private void validateRouteMode(IndexerActionItem action, boolean hasTargetEnvelope) {
		ActionDestination destination = ActionDestination.from(action);

		if (hasTargetEnvelope) {
			if (!isDocumentMutation(action)) {
				throw new IllegalArgumentException(
					"Target envelope supports only document mutation actions: " + action.getActionType()
				);
			}

			if (!destination.isEmpty()) {
				throw new IllegalArgumentException(
					"Target envelope actions must not include concrete destination fields"
				);
			}

			return;
		}

		if (timestamp != null) {
			throw new IllegalArgumentException("Timestamp is allowed only with target envelope routing");
		}

		if (destination.isEmpty()) {
			throw new IllegalArgumentException("Concrete action destination is required");
		}

		if (destination.indexerId() == null && destination.targetId() == null) {
			throw new IllegalArgumentException("Concrete action requires target id or indexer id");
		}

		if (!isDocumentMutation(action) && destination.indexerId() == null) {
			throw new IllegalArgumentException(
				"Internal action requires concrete indexer id: " + action.getActionType()
			);
		}
	}

	private boolean isDocumentMutation(IndexerActionItem action) {
		return action.getActionType() == IndexerActionType.PUT_DOCUMENT
			|| action.getActionType() == IndexerActionType.REMOVE_DOCUMENT;
	}
}
