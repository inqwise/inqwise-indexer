package com.inqwise.indexer.service.admin;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminIndexerResult {
	public static final class Keys {
		public static final String INDEXER = "indexer";

		private Keys() {
		}
	}

	private AdminIndexerView indexer;

	public AdminIndexerResult() {
	}

	public AdminIndexerResult(JsonObject json) {
		JsonObject indexerJson = json.getJsonObject(Keys.INDEXER);
		this.indexer = indexerJson == null ? null : new AdminIndexerView(indexerJson);
	}

	public JsonObject toJson() {
		return new JsonObject().put(Keys.INDEXER, indexer == null ? null : indexer.toJson());
	}

	public AdminIndexerView getIndexer() {
		return indexer;
	}

	public AdminIndexerResult setIndexer(AdminIndexerView indexer) {
		this.indexer = indexer;
		return this;
	}
}
