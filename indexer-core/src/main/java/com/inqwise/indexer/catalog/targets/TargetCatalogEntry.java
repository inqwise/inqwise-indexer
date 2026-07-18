package com.inqwise.indexer.catalog.targets;

import java.time.Instant;
import java.util.Objects;

public record TargetCatalogEntry(
	Integer id,
	String uid,
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
	public TargetCatalogEntry {
		id = Objects.requireNonNull(id, "id");
		uid = Objects.requireNonNull(uid, "uid");
		targetName = Objects.requireNonNull(targetName, "targetName");
		status = Objects.requireNonNull(status, "status");
		provisioningState = Objects.requireNonNull(provisioningState, "provisioningState");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private String uid;
		private String targetName;
		private String periodKey;
		private Instant periodStartInclusive;
		private Instant periodEndExclusive;
		private TargetStatus status;
		private TargetProvisioningState provisioningState;
		private Instant createdAt;
		private Instant updatedAt;
		private long version;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withUid(String value) {
			uid = value;
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

		public TargetCatalogEntry build() {
			return new TargetCatalogEntry(
				id,
				uid,
				targetName,
				periodKey,
				periodStartInclusive,
				periodEndExclusive,
				status,
				provisioningState,
				createdAt,
				updatedAt,
				version
			);
		}
	}
}
