package com.inqwise.indexer;

import io.vertx.core.json.JsonObject;

public class IndexerSnapshot {
	private final IndexerModel model;
	private final String queueName;
	private final IndexerSnapshot nextIndexer;

	IndexerSnapshot(IndexerModel model, String queueName, IndexerSnapshot nextIndexer) {
		this.model = model;
		this.queueName = queueName;
		this.nextIndexer = nextIndexer;
	}

	public JsonObject toJson() {
		JsonObject json = model.toJson().put("resolved_queue_name", queueName);
		if (nextIndexer != null) {
			json.put("next", nextIndexer.toJson());
		}
		return json;
	}
}
