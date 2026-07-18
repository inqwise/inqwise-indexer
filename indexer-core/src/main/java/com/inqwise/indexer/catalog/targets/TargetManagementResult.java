package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

public record TargetManagementResult(
	Integer targetId,
	String targetName,
	TargetStatus status,
	TargetProvisioningState provisioningState,
	long version
) {
	public TargetManagementResult {
		targetId = Objects.requireNonNull(targetId, "targetId");
		targetName = Objects.requireNonNull(targetName, "targetName");
		status = Objects.requireNonNull(status, "status");
		provisioningState = Objects.requireNonNull(provisioningState, "provisioningState");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private String targetName;
		private TargetStatus status;
		private TargetProvisioningState provisioningState;
		private long version;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withStatus(TargetStatus value) {
			status = value;
			return this;
		}

		public Builder withProvisioningState(TargetProvisioningState value) {
			provisioningState = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public TargetManagementResult build() {
			return new TargetManagementResult(
				targetId,
				targetName,
				status,
				provisioningState,
				version
			);
		}
	}
}
