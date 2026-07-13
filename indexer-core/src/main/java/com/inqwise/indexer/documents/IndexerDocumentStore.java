package com.inqwise.indexer.documents;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface IndexerDocumentStore {
	Future<Void> put(String indexName, String uid, JsonObject document);

	Future<Void> remove(String indexName, String uid);

	Future<Void> drop(String indexName);
}
