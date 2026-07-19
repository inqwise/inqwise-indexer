package com.inqwise.indexer.runtime;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerQueueConsumerOptions {
	public static final String TARGET_NAME = "target_name";
	public static final String QUEUE_NAME = "queue_name";
	public static final String BULK_SIZE = "bulk_size";

	private static final int DEFAULT_BULK_SIZE = 100;

	private final String targetName;
	private final String queueName;
	private final int bulkSize;

	public IndexerQueueConsumerOptions(String targetName, String queueName, int bulkSize) {
		this.targetName = targetName;
		this.queueName = queueName;
		this.bulkSize = bulkSize <= 0 ? DEFAULT_BULK_SIZE : bulkSize;
	}

	public IndexerQueueConsumerOptions(JsonObject json) {
		this(
			json.getString(TARGET_NAME),
			json.getString(QUEUE_NAME),
			json.getInteger(BULK_SIZE, DEFAULT_BULK_SIZE)
		);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(TARGET_NAME, targetName)
			.put(QUEUE_NAME, queueName)
			.put(BULK_SIZE, bulkSize);
	}

	public String getTargetName() {
		return targetName;
	}

	public String getQueueName() {
		return queueName;
	}

	public int getBulkSize() {
		return bulkSize;
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}

	public static final class Builder {
		private String targetName;
		private String queueName;
		private int bulkSize = DEFAULT_BULK_SIZE;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withQueueName(String value) {
			queueName = value;
			return this;
		}

		public Builder withBulkSize(int value) {
			bulkSize = value;
			return this;
		}

		public IndexerQueueConsumerOptions build() {
			return new IndexerQueueConsumerOptions(
				requireText(targetName, "targetName"),
				requireText(queueName, "queueName"),
				bulkSize
			);
		}
	}
}
