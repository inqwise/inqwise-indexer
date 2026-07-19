package com.inqwise.indexer.service.runtime;

import java.util.Objects;

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

	public static Builder builder() {
		return new Builder();
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

	public static final class Builder {
		private Integer indexerId;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public RuntimeReconcileRequest build() {
			return new RuntimeReconcileRequest()
				.setIndexerId(Objects.requireNonNull(indexerId, "indexerId"));
		}
	}
}
