package com.inqwise.indexer.runtime;

import java.util.Objects;

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
		this(DEFAULT_QUEUE_NAME_PREFIX, DEFAULT_BULK_SIZE);
	}

	public IndexerOptions(JsonObject json) {
		this.queueNamePrefix = json.getString(QUEUE_NAME_PREFIX, DEFAULT_QUEUE_NAME_PREFIX);
		this.bulkSize = json.getInteger(BULK_SIZE, DEFAULT_BULK_SIZE);
	}

	private IndexerOptions(String queueNamePrefix, int bulkSize) {
		this.queueNamePrefix = queueNamePrefix;
		this.bulkSize = bulkSize;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getQueueNamePrefix() {
		return queueNamePrefix;
	}

	public int getBulkSize() {
		return bulkSize;
	}

	public static final class Builder {
		private String queueNamePrefix = DEFAULT_QUEUE_NAME_PREFIX;
		private int bulkSize = DEFAULT_BULK_SIZE;

		private Builder() {
		}

		public Builder withQueueNamePrefix(String value) {
			queueNamePrefix = value;
			return this;
		}

		public Builder withBulkSize(int value) {
			bulkSize = value;
			return this;
		}

		public IndexerOptions build() {
			Objects.requireNonNull(queueNamePrefix, "queueNamePrefix");
			if (queueNamePrefix.isBlank()) {
				throw new IllegalArgumentException("queueNamePrefix must not be blank");
			}
			if (bulkSize <= 0) {
				throw new IllegalArgumentException("bulkSize must be positive");
			}
			return new IndexerOptions(queueNamePrefix, bulkSize);
		}
	}
}
