package com.inqwise.indexer.hot;

import java.time.Duration;
import java.util.Objects;

public record TargetInvalidationRegistryOptions(
	Duration pollInterval,
	int retentionFactor
) {
	public static final int MIN_RETENTION_FACTOR = 2;

	public TargetInvalidationRegistryOptions {
		Objects.requireNonNull(pollInterval, "pollInterval");
		if (pollInterval.isZero() || pollInterval.isNegative()) {
			throw new IllegalArgumentException("pollInterval must be positive");
		}

		if (retentionFactor < MIN_RETENTION_FACTOR) {
			throw new IllegalArgumentException(
				"retentionFactor must be at least " + MIN_RETENTION_FACTOR
			);
		}
	}

	public Duration ttl() {
		return pollInterval.multipliedBy(retentionFactor);
	}
}
