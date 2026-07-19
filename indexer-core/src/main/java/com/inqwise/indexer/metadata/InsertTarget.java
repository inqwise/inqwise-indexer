package com.inqwise.indexer.metadata;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

public record InsertTarget(
	String prefix,
	String targetName,
	String periodKey,
	Instant periodStartInclusive,
	Instant periodEndExclusive,
	TargetStatus status,
	TargetProvisioningState provisioningState
) {
	public InsertTarget {
		Objects.requireNonNull(prefix, "prefix");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String prefix;
		private String targetName;
		private String periodKey;
		private Instant periodStartInclusive;
		private Instant periodEndExclusive;
		private TargetStatus status;
		private TargetProvisioningState provisioningState;

		private Builder() {
		}

		public Builder withPrefix(String value) {
			prefix = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withPeriodKey(String value) {
			periodKey = value;
			return this;
		}

		public Builder withPeriodStartInclusive(Instant value) {
			periodStartInclusive = value;
			return this;
		}

		public Builder withPeriodEndExclusive(Instant value) {
			periodEndExclusive = value;
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

		public InsertTarget build() {
			return new InsertTarget(
				Objects.requireNonNull(prefix, "prefix"),
				Objects.requireNonNull(targetName, "targetName"),
				periodKey,
				periodStartInclusive,
				periodEndExclusive,
				Objects.requireNonNull(status, "status"),
				Objects.requireNonNull(provisioningState, "provisioningState")
			);
		}
	}
}
