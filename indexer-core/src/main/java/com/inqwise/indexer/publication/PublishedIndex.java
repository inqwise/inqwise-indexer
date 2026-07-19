package com.inqwise.indexer.publication;

import java.util.Objects;

public record PublishedIndex(
	Integer indexerId,
	Integer targetId,
	String indexName
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private Integer targetId;
		private String indexName;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public PublishedIndex build() {
			return new PublishedIndex(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(indexName, "indexName")
			);
		}
	}
}
