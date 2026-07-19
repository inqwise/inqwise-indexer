package com.inqwise.indexer.provisioning;

import java.util.Objects;

public record GeneratedIndexerResources(
	String prefix,
	String indexName,
	String queueName
) {
	public GeneratedIndexerResources {
		prefix = Objects.requireNonNull(prefix, "prefix");
		indexName = Objects.requireNonNull(indexName, "indexName");
		queueName = Objects.requireNonNull(queueName, "queueName");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String prefix;
		private String indexName;
		private String queueName;

		private Builder() {
		}

		public Builder withPrefix(String value) {
			prefix = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withQueueName(String value) {
			queueName = value;
			return this;
		}

		public GeneratedIndexerResources build() {
			return new GeneratedIndexerResources(prefix, indexName, queueName);
		}
	}
}
