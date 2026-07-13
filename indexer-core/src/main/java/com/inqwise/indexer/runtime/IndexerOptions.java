package com.inqwise.indexer.runtime;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerOptions {
	public static final String QUEUE_NAME_PREFIX = "queue_name_prefix";
	public static final String BULK_SIZE = "bulk_size";
	private static final String DEFAULT_QUEUE_NAME_PREFIX = "inqwise_indexer_";
	private static final int DEFAULT_BULK_SIZE = 100;

	private final String queueNamePrefix;
	private final int bulkSize;

	public IndexerOptions() {
		this.queueNamePrefix = DEFAULT_QUEUE_NAME_PREFIX;
		this.bulkSize = DEFAULT_BULK_SIZE;
	}

	public IndexerOptions(JsonObject json) {
		this.queueNamePrefix = json.getString(QUEUE_NAME_PREFIX, DEFAULT_QUEUE_NAME_PREFIX);
		this.bulkSize = json.getInteger(BULK_SIZE, DEFAULT_BULK_SIZE);
	}

	public String getQueueNamePrefix() {
		return queueNamePrefix;
	}

	public int getBulkSize() {
		return bulkSize;
	}
}
