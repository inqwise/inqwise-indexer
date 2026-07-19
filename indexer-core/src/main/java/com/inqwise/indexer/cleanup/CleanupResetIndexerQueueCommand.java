package com.inqwise.indexer.cleanup;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public final class CleanupResetIndexerQueueCommand implements Command {
	public static final String TYPE = "indexer.queue.cleanup-reset";

	private final Integer indexerId;
	private final String queueName;

	public CleanupResetIndexerQueueCommand(Integer indexerId, String queueName) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.queueName = Objects.requireNonNull(queueName, "queueName");
	}

	public CleanupResetIndexerQueueCommand(JsonObject json) {
		this(
			json.getInteger("indexer_id"),
			json.getString("queue_name")
		);
	}

	public static CleanupResetIndexerQueueCommand fromJson(JsonObject json) {
		Objects.requireNonNull(json, "json");
		return builder()
			.withIndexerId(json.getInteger("indexer_id"))
			.withQueueName(json.getString("queue_name"))
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public String getQueueName() {
		return queueName;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("queue_name", queueName);
	}

	public static final class Builder {
		private Integer indexerId;
		private String queueName;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withQueueName(String value) {
			queueName = value;
			return this;
		}

		public CleanupResetIndexerQueueCommand build() {
			return new CleanupResetIndexerQueueCommand(indexerId, queueName);
		}
	}
}
