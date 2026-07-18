package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadState;

import java.util.Objects;

public record UpdateIndexerLoadState(
	Integer indexerId,
	IndexerLoadState state,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private IndexerLoadState state;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withState(IndexerLoadState value) {
			state = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateIndexerLoadState build() {
			return new UpdateIndexerLoadState(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(state, "state"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
