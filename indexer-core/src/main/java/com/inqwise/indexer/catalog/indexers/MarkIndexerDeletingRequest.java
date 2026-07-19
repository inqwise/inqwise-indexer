package com.inqwise.indexer.catalog.indexers;

import java.util.Objects;

public record MarkIndexerDeletingRequest(
	Integer indexerId,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public MarkIndexerDeletingRequest build() {
			return new MarkIndexerDeletingRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
