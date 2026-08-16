package com.inqwise.indexer.actions;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public interface IndexerActionItem {
	String TYPE = "type";

	IndexerActionType getActionType();

	JsonObject toJson();

	static IndexerActionItem fromJson(JsonObject json) {
		JsonObject action = Objects.requireNonNull(json, "json");
		String type = ActionItemValidation.requiredText(action.getString(TYPE), "type");
		IndexerActionType actionType;
		try {
			actionType = IndexerActionType.valueOf(type);
		} catch (IllegalArgumentException error) {
			throw new IllegalArgumentException("Unknown action type: " + type, error);
		}

		return switch (actionType) {
			case PUT_DOCUMENT -> new PutDocumentActionItem(action);
			case REMOVE_DOCUMENT -> new RemoveDocumentActionItem(action);
			case COMPLETE -> new CompleteIndexActionItem(action);
			case CATCH_UP_BARRIER -> new CatchUpBarrierActionItem(action);
		};
	}
}
