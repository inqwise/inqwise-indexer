package com.inqwise.indexer.service.runtime;

import java.util.List;

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

	public JsonObject toJson() {
		return new JsonObject().put(Keys.INDEXERS, new JsonArray(indexers.stream()
			.map(RuntimeIndexerStatus::toJson)
			.toList()));
	}

	public static RuntimeStatusResult from(List<IndexerSnapshot> snapshots) {
		return new RuntimeStatusResult()
			.setIndexers(snapshots.stream()
				.map(RuntimeIndexerStatus::from)
				.toList());
	}

	public List<RuntimeIndexerStatus> getIndexers() {
		return indexers;
	}

	public RuntimeStatusResult setIndexers(List<RuntimeIndexerStatus> indexers) {
		this.indexers = indexers == null ? List.of() : List.copyOf(indexers);
		return this;
	}
}
