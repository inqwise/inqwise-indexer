package com.inqwise.indexer.routing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SubmitIndexActionsCommand implements Command {
	public static final String TYPE = "indexer.actions.submit";
	public static final int MAX_ACTIONS = 1000;
	public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;

	private final String correlationId;
	private final String targetName;
	private final Instant timestamp;
	private final List<IndexerActionItem> actions;

	SubmitIndexActionsCommand(
		List<IndexerActionItem> actions
	) {
		this(builder().withActions(actions));
	}

	SubmitIndexActionsCommand(
		String correlationId,
		List<IndexerActionItem> actions
	) {
		this(builder()
			.withCorrelationId(correlationId)
			.withActions(actions));
	}

	SubmitIndexActionsCommand(
		String targetName,
		Instant timestamp,
		List<IndexerActionItem> actions
	) {
		this(builder()
			.withTargetName(targetName)
			.withTimestamp(timestamp)
			.withActions(actions));
	}

	SubmitIndexActionsCommand(
		String correlationId,
		String targetName,
		Instant timestamp,
		List<IndexerActionItem> actions
	) {
		this(builder()
			.withCorrelationId(correlationId)
			.withTargetName(targetName)
			.withTimestamp(timestamp)
			.withActions(actions));
	}

	SubmitIndexActionsCommand(JsonObject json, String correlationId) {
		this(builder(json, correlationId));
	}

	private SubmitIndexActionsCommand(Builder builder) {
		correlationId = Objects.requireNonNull(builder.correlationId, "correlationId");
		targetName = builder.targetName;
		timestamp = builder.timestamp;
		actions = validateActions(builder.actions);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(JsonObject json, String correlationId) {
		Objects.requireNonNull(json, "json");
		String timestamp = json.getString("timestamp");
		return builder()
			.withCorrelationId(correlationId)
			.withTargetName(json.getString("target_name"))
			.withTimestamp(timestamp == null ? null : Instant.parse(timestamp))
			.withActions(json.getJsonArray("actions", new JsonArray()).stream()
				.map(JsonObject.class::cast)
				.map(IndexerActionItem::fromJson)
				.toList());
	}

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public String getCorrelationId() {
		return correlationId;
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
		JsonObject json = new JsonObject()
			.put("actions", new JsonArray(actions.stream()
				.map(IndexerActionItem::toJson)
				.toList()));

		if (targetName != null) {
			json.put("target_name", targetName);
		}

		if (timestamp != null) {
			json.put("timestamp", timestamp.toString());
		}

		return json;
	}

	private List<IndexerActionItem> validateActions(List<IndexerActionItem> actions) {
		List<IndexerActionItem> copy = List.copyOf(Objects.requireNonNull(actions, "actions"));
		if (copy.isEmpty()) {
			throw new IllegalArgumentException("No actions submitted");
		}

		if (copy.size() > MAX_ACTIONS) {
			throw new IllegalArgumentException("Too many actions submitted: " + copy.size());
		}

		boolean hasTargetEnvelope = targetName != null;
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
					"Target envelope actions must not include route destination fields"
				);
			}

			return;
		}

		if (timestamp != null) {
			throw new IllegalArgumentException("Timestamp is allowed only with target envelope routing");
		}

		if (destination.isEmpty()) {
			throw new IllegalArgumentException("Routed command action destination is required");
		}

		if (destination.indexerId() == null && destination.targetId() == null) {
			throw new IllegalArgumentException("Routed command action requires target id or indexer id");
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

	public static final class Builder {
		private String correlationId = UUID.randomUUID().toString();
		private String targetName;
		private Instant timestamp;
		private List<IndexerActionItem> actions;

		private Builder() {
		}

		public Builder withCorrelationId(String value) {
			correlationId = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withTimestamp(Instant value) {
			timestamp = value;
			return this;
		}

		public Builder withActions(List<IndexerActionItem> value) {
			actions = value == null ? null : List.copyOf(value);
			return this;
		}

		public SubmitIndexActionsCommand build() {
			return new SubmitIndexActionsCommand(this);
		}
	}
}
