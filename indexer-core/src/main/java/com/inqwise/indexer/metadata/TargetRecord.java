package com.inqwise.indexer.metadata;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

public record TargetRecord(
	Integer id,
	String prefix,
	String targetName,
	String periodKey,
	Instant periodStartInclusive,
	Instant periodEndExclusive,
	TargetStatus status,
	TargetProvisioningState provisioningState,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public static Builder builder() {
		return new Builder();
	}

	public String uid() {
		return MetadataUid.toToken(prefix, id);
	}

	public static final class Builder {
		private Integer id;
		private String prefix;
		private String targetName;
		private String periodKey;
		private Instant periodStartInclusive;
		private Instant periodEndExclusive;
		private TargetStatus status;
		private TargetProvisioningState provisioningState;
		private Instant createdAt;
		private Instant updatedAt;
		private Long version;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
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

		public Builder withCreatedAt(Instant value) {
			createdAt = value;
			return this;
		}

		public Builder withUpdatedAt(Instant value) {
			updatedAt = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public TargetRecord build() {
			return new TargetRecord(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(prefix, "prefix"),
				Objects.requireNonNull(targetName, "targetName"),
				periodKey,
				periodStartInclusive,
				periodEndExclusive,
				Objects.requireNonNull(status, "status"),
				Objects.requireNonNull(provisioningState, "provisioningState"),
				Objects.requireNonNull(createdAt, "createdAt"),
				Objects.requireNonNull(updatedAt, "updatedAt"),
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
