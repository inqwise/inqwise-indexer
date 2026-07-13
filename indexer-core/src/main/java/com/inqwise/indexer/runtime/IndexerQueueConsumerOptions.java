package com.inqwise.indexer.runtime;

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
}
