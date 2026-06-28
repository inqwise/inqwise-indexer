package com.inqwise.indexer.commands;

import java.util.Objects;

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
}
