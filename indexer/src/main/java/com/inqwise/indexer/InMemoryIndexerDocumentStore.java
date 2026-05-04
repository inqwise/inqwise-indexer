package com.inqwise.indexer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class InMemoryIndexerDocumentStore implements IndexerDocumentStore {
  private final Map<String, Map<String, JsonObject>> indexes = new ConcurrentHashMap<>();

  @Override
  public Future<Void> put(String indexName, String uid, JsonObject document) {
    indexes.computeIfAbsent(indexName, ignored -> new ConcurrentHashMap<>()).put(uid, document.copy());
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> remove(String indexName, String uid) {
    Map<String, JsonObject> index = indexes.get(indexName);
    if (index != null) {
      index.remove(uid);
    }
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> drop(String indexName) {
    indexes.remove(indexName);
    return Future.succeededFuture();
  }

  public JsonObject get(String indexName, String uid) {
    Map<String, JsonObject> index = indexes.get(indexName);
    JsonObject document = index == null ? null : index.get(uid);
    return document == null ? null : document.copy();
  }
}
