package com.inqwise.indexer.service.runtime;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.runtime.IndexerSnapshot;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class RuntimeStatusResult {
	public static final class Keys {
		public static final String INDEXERS = "indexers";

		private Keys() {
		}
	}

	private List<RuntimeIndexerStatus> indexers = List.of();

	public RuntimeStatusResult() {
	}

	public RuntimeStatusResult(JsonObject json) {
		this.indexers = json.getJsonArray(Keys.INDEXERS, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(RuntimeIndexerStatus::new)
			.toList();
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject().put(Keys.INDEXERS, new JsonArray(indexers.stream()
			.map(RuntimeIndexerStatus::toJson)
			.toList()));
	}

	public static RuntimeStatusResult from(List<IndexerSnapshot> snapshots) {
		Objects.requireNonNull(snapshots, "snapshots");
		return builder()
			.withIndexers(snapshots.stream()
				.map(RuntimeIndexerStatus::from)
				.toList())
			.build();
	}

	public List<RuntimeIndexerStatus> getIndexers() {
		return indexers;
	}

	public RuntimeStatusResult setIndexers(List<RuntimeIndexerStatus> indexers) {
		this.indexers = indexers == null ? List.of() : List.copyOf(indexers);
		return this;
	}

	public static final class Builder {
		private List<RuntimeIndexerStatus> indexers = List.of();

		private Builder() {
		}

		public Builder withIndexers(List<RuntimeIndexerStatus> value) {
			indexers = value == null ? null : List.copyOf(value);
			return this;
		}

		public RuntimeStatusResult build() {
			return new RuntimeStatusResult()
				.setIndexers(Objects.requireNonNull(indexers, "indexers"));
		}
	}
}
