package com.inqwise.indexer.service.admin;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminIndexerLifecycleRequest {
	public static final class Keys {
		public static final String INDEXER_ID = "indexer_id";

		private Keys() {
		}
	}

	private Integer indexerId;

	public AdminIndexerLifecycleRequest() {
	}

	public AdminIndexerLifecycleRequest(JsonObject json) {
		this.indexerId = json.getInteger(Keys.INDEXER_ID);
	}

	public JsonObject toJson() {
		return new JsonObject().put(Keys.INDEXER_ID, indexerId);
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public AdminIndexerLifecycleRequest setIndexerId(Integer indexerId) {
		this.indexerId = indexerId;
		return this;
	}
}
