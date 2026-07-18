package com.inqwise.indexer.service.indexer;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerResult {
	private IndexerView indexer;

	public IndexerResult() {
	}

	public IndexerResult(JsonObject json) {
		JsonObject indexerJson = json.getJsonObject("indexer");
		indexer = indexerJson == null ? null : new IndexerView(indexerJson);
	}

	public JsonObject toJson() {
		return new JsonObject().put("indexer", indexer == null ? null : indexer.toJson());
	}

	public IndexerView getIndexer() {
		return indexer;
	}

	public IndexerResult setIndexer(IndexerView value) {
		indexer = value;
		return this;
	}
}
