package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;

public record UpdateIndexerProvisioningState(
	Integer id,
	IndexerProvisioningState provisioningState,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private IndexerProvisioningState provisioningState;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withProvisioningState(IndexerProvisioningState value) {
			provisioningState = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateIndexerProvisioningState build() {
			return new UpdateIndexerProvisioningState(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(provisioningState, "provisioningState"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
