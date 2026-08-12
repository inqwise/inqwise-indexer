package com.inqwise.indexer.load.service;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class LoadListResult {
	private List<LoadView> loads = List.of();

	public LoadListResult() {
	}

	public LoadListResult(JsonObject json) {
		loads = json.getJsonArray("loads", new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(LoadView::new)
			.toList();
	}

	public JsonObject toJson() {
		return new JsonObject().put("loads", new JsonArray(loads.stream()
			.map(LoadView::toJson)
			.toList()));
	}

	public List<LoadView> getLoads() {
		return List.copyOf(loads);
	}

	public LoadListResult setLoads(List<LoadView> value) {
		loads = value == null ? List.of() : List.copyOf(value);
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<LoadView> loads = List.of();

		private Builder() {
		}

		public Builder withLoads(List<LoadView> value) {
			loads = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public LoadListResult build() {
			return new LoadListResult().setLoads(loads);
		}
	}
}
