package com.inqwise.indexer.service.runtime;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class RuntimeReconcileRequest {
	public static final class Keys {
		public static final String INDEXER_ID = "indexer_id";

		private Keys() {
		}
	}

	private Integer indexerId;

	public RuntimeReconcileRequest() {
	}

	public RuntimeReconcileRequest(JsonObject json) {
		this.indexerId = json.getInteger(Keys.INDEXER_ID);
	}

	public JsonObject toJson() {
		return new JsonObject().put(Keys.INDEXER_ID, indexerId);
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public RuntimeReconcileRequest setIndexerId(Integer indexerId) {
		this.indexerId = indexerId;
		return this;
	}
}
