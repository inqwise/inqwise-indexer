package com.inqwise.indexer.runtime;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerModel;

import io.vertx.core.json.JsonObject;

public class IndexerSnapshot {
	private final IndexerModel model;
	private final String queueName;

	private IndexerSnapshot(IndexerModel model, String queueName) {
		this.model = model;
		this.queueName = queueName;
	}

	public static Builder builder() {
		return new Builder();
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

	public static final class Builder {
		private IndexerModel model;
		private String queueName;

		private Builder() {
		}

		public Builder withModel(IndexerModel value) {
			model = value;
			return this;
		}

		public Builder withQueueName(String value) {
			queueName = value;
			return this;
		}

		public IndexerSnapshot build() {
			return new IndexerSnapshot(
				Objects.requireNonNull(model, "model"),
				Objects.requireNonNull(queueName, "queueName")
			);
		}
	}
}
