package com.inqwise.indexer.service.indexer;

import java.util.List;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerListResult {
	private List<IndexerView> indexers = List.of();

	public IndexerListResult() {
	}

	public IndexerListResult(JsonObject json) {
		indexers = json.getJsonArray("indexers", new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(IndexerView::new)
			.toList();
	}

	public JsonObject toJson() {
		return new JsonObject().put("indexers", new JsonArray(indexers.stream()
			.map(IndexerView::toJson)
			.toList()));
	}

	public List<IndexerView> getIndexers() {
		return indexers;
	}

	public IndexerListResult setIndexers(List<IndexerView> values) {
		indexers = values == null ? List.of() : List.copyOf(values);
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<IndexerView> indexers = List.of();

		private Builder() {
		}

		public Builder withIndexers(List<IndexerView> values) {
			indexers = values == null ? List.of() : List.copyOf(values);
			return this;
		}

		public IndexerListResult build() {
			return new IndexerListResult().setIndexers(indexers);
		}
	}
}
