package com.inqwise.indexer.commands;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class ResetIndexerQueueCommand implements Command {
	public static final String TYPE = "indexer.queue.reset";

	private final Integer indexerId;
	private final String expectedQueueName;
	private final long expectedVersion;

	public ResetIndexerQueueCommand(
		Integer indexerId,
		String expectedQueueName,
		long expectedVersion
	) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.expectedQueueName = Objects.requireNonNull(
			expectedQueueName,
			"expectedQueueName"
		);
		this.expectedVersion = expectedVersion;
	}

	public ResetIndexerQueueCommand(JsonObject json) {
		this(
			json.getInteger("indexer_id"),
			json.getString("expected_queue_name"),
			json.getLong("expected_version", 0L)
		);
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public String getExpectedQueueName() {
		return expectedQueueName;
	}

	public long getExpectedVersion() {
		return expectedVersion;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("expected_queue_name", expectedQueueName)
			.put("expected_version", expectedVersion);
	}
}
