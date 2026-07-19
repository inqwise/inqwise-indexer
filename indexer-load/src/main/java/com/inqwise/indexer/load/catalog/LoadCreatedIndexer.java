package com.inqwise.indexer.load.catalog;

import java.util.Objects;

public record LoadCreatedIndexer(
	Integer id,
	Integer targetId,
	String prefix,
	String indexName,
	String queueName,
	Long version
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private Integer targetId;
		private String prefix;
		private String indexName;
		private String queueName;
		private Long version;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
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

		public Builder withVersion(Long value) {
			version = value;
			return this;
		}

		public LoadCreatedIndexer build() {
			return new LoadCreatedIndexer(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(prefix, "prefix"),
				Objects.requireNonNull(indexName, "indexName"),
				Objects.requireNonNull(queueName, "queueName"),
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
