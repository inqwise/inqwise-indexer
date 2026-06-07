package com.inqwise.indexer;

import io.vertx.core.json.JsonObject;

public class IndexerSnapshot {
	private final IndexerModel model;
	private final String queueName;

	IndexerSnapshot(IndexerModel model, String queueName) {
		this.model = model;
		this.queueName = queueName;
	}

	public IndexerModel getModel() {
		return model;
	}

	public String getQueueName() {
		return queueName;
	}

	public JsonObject toJson() {
		return model.toJson().put("resolved_queue_name", queueName);
	}
}
