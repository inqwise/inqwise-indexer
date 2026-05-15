package com.inqwise.indexer;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class CompleteIndexActionItem implements IndexerActionItem {
	public static final String TYPE = "type";

	public CompleteIndexActionItem() {
	}

	public CompleteIndexActionItem(JsonObject json) {
	}

	@Override
	public IndexerActionType getActionType() {
		return IndexerActionType.COMPLETE;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put(TYPE, getActionType().name());
	}
}
