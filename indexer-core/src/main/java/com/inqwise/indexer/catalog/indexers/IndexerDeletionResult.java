package com.inqwise.indexer.catalog.indexers;

import java.util.Objects;

public record IndexerDeletionResult(
	Integer indexerId,
	Integer targetId,
	MutationState mutationState,
	IndexerRuntimeState runtimeState,
	long version
) {
	public IndexerDeletionResult {
		indexerId = Objects.requireNonNull(indexerId, "indexerId");
		targetId = Objects.requireNonNull(targetId, "targetId");
		mutationState = Objects.requireNonNull(mutationState, "mutationState");
		runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private Integer targetId;
		private MutationState mutationState;
		private IndexerRuntimeState runtimeState;
		private Long version;

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

		public Builder withMutationState(MutationState value) {
			mutationState = value;
			return this;
		}

		public Builder withRuntimeState(IndexerRuntimeState value) {
			runtimeState = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public IndexerDeletionResult build() {
			return new IndexerDeletionResult(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(mutationState, "mutationState"),
				Objects.requireNonNull(runtimeState, "runtimeState"),
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
