package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.catalog.targets.TargetProvisioningState;

public record UpdateTargetProvisioningState(
	Integer id,
	TargetProvisioningState provisioningState,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private TargetProvisioningState provisioningState;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withProvisioningState(TargetProvisioningState value) {
			provisioningState = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateTargetProvisioningState build() {
			return new UpdateTargetProvisioningState(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(provisioningState, "provisioningState"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
