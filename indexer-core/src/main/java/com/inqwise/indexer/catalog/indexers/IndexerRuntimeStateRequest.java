package com.inqwise.indexer.catalog.indexers;

import java.util.Objects;

public record IndexerRuntimeStateRequest(Integer indexerId, long expectedVersion) {
	public IndexerRuntimeStateRequest {
		Objects.requireNonNull(indexerId, "indexerId");
	}

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

		public IndexerRuntimeStateRequest build() {
			return new IndexerRuntimeStateRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
