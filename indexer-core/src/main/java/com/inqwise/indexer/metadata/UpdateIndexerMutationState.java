package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.MutationState;

public record UpdateIndexerMutationState(
	Integer id,
	MutationState mutationState,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private MutationState mutationState;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withMutationState(MutationState value) {
			mutationState = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateIndexerMutationState build() {
			return new UpdateIndexerMutationState(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(mutationState, "mutationState"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
