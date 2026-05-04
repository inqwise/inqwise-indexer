package com.inqwise.indexer;

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
    };
  }
}
