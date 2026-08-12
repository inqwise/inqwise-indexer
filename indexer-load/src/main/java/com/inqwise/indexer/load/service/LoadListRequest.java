package com.inqwise.indexer.load.service;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class LoadListRequest {
	private Integer max;

	public LoadListRequest() {
	}

	public LoadListRequest(JsonObject json) {
		max = json.getInteger("max");
	}

	public JsonObject toJson() {
		return new JsonObject().put("max", max);
	}

	public Integer getMax() {
		return max;
	}

	public LoadListRequest setMax(Integer value) {
		max = value;
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer max;

		private Builder() {
		}

		public Builder withMax(Integer value) {
			max = value;
			return this;
		}

		public LoadListRequest build() {
			return new LoadListRequest().setMax(max);
		}
	}
}
