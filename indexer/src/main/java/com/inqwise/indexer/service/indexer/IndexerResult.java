package com.inqwise.indexer.service.indexer;

import java.util.Objects;

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

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private IndexerView indexer;

		private Builder() {
		}

		public Builder withIndexer(IndexerView value) {
			indexer = value;
			return this;
		}

		public IndexerResult build() {
			return new IndexerResult()
				.setIndexer(Objects.requireNonNull(indexer, "indexer"));
		}
	}
}
