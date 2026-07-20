package com.inqwise.indexer.service.admin;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminIndexerListResult {
	public static final class Keys {
		public static final String INDEXERS = "indexers";

		private Keys() {
		}
	}

	private List<AdminIndexerView> indexers = List.of();

	public AdminIndexerListResult() {
	}

	public AdminIndexerListResult(JsonObject json) {
		this.indexers = json.getJsonArray(Keys.INDEXERS, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(AdminIndexerView::new)
			.toList();
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject().put(Keys.INDEXERS, new JsonArray(indexers.stream()
			.map(AdminIndexerView::toJson)
			.toList()));
	}

	public List<AdminIndexerView> getIndexers() {
		return indexers;
	}

	public AdminIndexerListResult setIndexers(List<AdminIndexerView> indexers) {
		this.indexers = indexers == null ? List.of() : List.copyOf(indexers);
		return this;
	}

	public static final class Builder {
		private List<AdminIndexerView> indexers = List.of();

		private Builder() {
		}

		public Builder withIndexers(List<AdminIndexerView> value) {
			indexers = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public AdminIndexerListResult build() {
			return new AdminIndexerListResult().setIndexers(indexers);
		}
	}
}
