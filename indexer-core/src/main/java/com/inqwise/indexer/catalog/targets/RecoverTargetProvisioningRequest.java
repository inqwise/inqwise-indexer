package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

public record RecoverTargetProvisioningRequest(Integer targetId, long expectedVersion) {
	public RecoverTargetProvisioningRequest {
		Objects.requireNonNull(targetId, "targetId");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public RecoverTargetProvisioningRequest build() {
			return new RecoverTargetProvisioningRequest(
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
