package com.inqwise.indexer.actions;

import io.vertx.core.json.JsonObject;

public interface IndexerActionItem {
	String TYPE = "type";

	IndexerActionType getActionType();

	JsonObject toJson();

	static IndexerActionItem fromJson(JsonObject json) {
		IndexerActionType actionType = IndexerActionType.valueOf(json.getString(TYPE));

		return switch (actionType) {
			case PUT_DOCUMENT -> new PutDocumentActionItem(json);
			case REMOVE_DOCUMENT -> new RemoveDocumentActionItem(json);
			case COMPLETE -> new CompleteIndexActionItem(json);
			case CATCH_UP_BARRIER -> new CatchUpBarrierActionItem(json);
		};
	}
}
