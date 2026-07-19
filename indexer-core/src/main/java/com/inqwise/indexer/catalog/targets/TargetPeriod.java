package com.inqwise.indexer.catalog.targets;

import java.time.Instant;
import java.util.Objects;

public record TargetPeriod(
	TargetPeriodStrategy strategy,
	String key,
	Instant startInclusive,
	Instant endExclusive
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private TargetPeriodStrategy strategy;
		private String key;
		private Instant startInclusive;
		private Instant endExclusive;

		private Builder() {
		}

		public Builder withStrategy(TargetPeriodStrategy value) {
			strategy = value;
			return this;
		}

		public Builder withKey(String value) {
			key = value;
			return this;
		}

		public Builder withStartInclusive(Instant value) {
			startInclusive = value;
			return this;
		}

		public Builder withEndExclusive(Instant value) {
			endExclusive = value;
			return this;
		}

		public TargetPeriod build() {
			TargetPeriodStrategy resolvedStrategy = Objects.requireNonNull(strategy, "strategy");
			if (resolvedStrategy != TargetPeriodStrategy.NONE) {
				Objects.requireNonNull(key, "key");
				Objects.requireNonNull(startInclusive, "startInclusive");
				Objects.requireNonNull(endExclusive, "endExclusive");
				if (!startInclusive.isBefore(endExclusive)) {
					throw new IllegalArgumentException("startInclusive must be before endExclusive");
				}
			}
			return new TargetPeriod(resolvedStrategy, key, startInclusive, endExclusive);
		}
	}
}
