package com.inqwise.indexer.query.provider;

import java.util.Map;

import io.vertx.core.json.JsonObject;

@FunctionalInterface
public interface DocumentSnapshotReader {
	Map<String, JsonObject> documents(String indexName);
}
