package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;

public record UpdateIndexerRuntimeState(
	Integer id,
	IndexerRuntimeState runtimeState,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private IndexerRuntimeState runtimeState;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withRuntimeState(IndexerRuntimeState value) {
			runtimeState = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateIndexerRuntimeState build() {
			return new UpdateIndexerRuntimeState(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(runtimeState, "runtimeState"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
