package com.inqwise.indexer.hot;

import java.time.Duration;
import java.util.Objects;

public record TargetInvalidationRegistryOptions(
	Duration pollInterval,
	int retentionFactor,
	int maxTargets
) {
	public static final int MIN_RETENTION_FACTOR = 2;

	public TargetInvalidationRegistryOptions {
		Objects.requireNonNull(pollInterval, "pollInterval");
		if (pollInterval.isZero() || pollInterval.isNegative()
			|| pollInterval.toMillis() == 0L) {
			throw new IllegalArgumentException("pollInterval must be at least one millisecond");
		}

		if (retentionFactor < MIN_RETENTION_FACTOR) {
			throw new IllegalArgumentException(
				"retentionFactor must be at least " + MIN_RETENTION_FACTOR
			);
		}

		if (maxTargets <= 0) {
			throw new IllegalArgumentException("maxTargets must be positive");
		}
	}

	public Duration ttl() {
		return pollInterval.multipliedBy(retentionFactor);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Duration pollInterval;
		private Integer retentionFactor;
		private Integer maxTargets;

		private Builder() {
		}

		public Builder withPollInterval(Duration value) {
			pollInterval = value;
			return this;
		}

		public Builder withRetentionFactor(int value) {
			retentionFactor = value;
			return this;
		}

		public Builder withMaxTargets(int value) {
			maxTargets = value;
			return this;
		}

		public TargetInvalidationRegistryOptions build() {
			return new TargetInvalidationRegistryOptions(
				Objects.requireNonNull(pollInterval, "pollInterval"),
				Objects.requireNonNull(retentionFactor, "retentionFactor"),
				Objects.requireNonNull(maxTargets, "maxTargets")
			);
		}
	}
}
