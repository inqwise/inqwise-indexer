package com.inqwise.indexer.hot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.providers.HotIndexerCapability;

public record HotConcreteTarget(
	Integer targetId,
	String targetName,
	String periodKey,
	Instant periodStartInclusive,
	Instant periodEndExclusive,
	List<HotIndexerCapability> liveWriters
) {
	public HotConcreteTarget {
		Objects.requireNonNull(targetId, "targetId");
		Objects.requireNonNull(targetName, "targetName");
		liveWriters = List.copyOf(Objects.requireNonNull(liveWriters, "liveWriters"));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private String targetName;
		private String periodKey;
		private Instant periodStartInclusive;
		private Instant periodEndExclusive;
		private List<HotIndexerCapability> liveWriters;

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

		public Builder withLiveWriters(List<HotIndexerCapability> value) {
			liveWriters = value == null ? null : List.copyOf(value);
			return this;
		}

		public HotConcreteTarget build() {
			return new HotConcreteTarget(
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(targetName, "targetName"),
				periodKey,
				periodStartInclusive,
				periodEndExclusive,
				Objects.requireNonNull(liveWriters, "liveWriters")
			);
		}
	}
}
