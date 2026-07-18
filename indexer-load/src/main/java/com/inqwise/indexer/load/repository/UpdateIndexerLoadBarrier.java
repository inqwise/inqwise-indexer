package com.inqwise.indexer.load.repository;

import java.time.Instant;
import java.util.Objects;

public record UpdateIndexerLoadBarrier(
	Integer indexerId,
	String barrierId,
	Instant barrierTimestamp,
	Instant reachedAt,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private String barrierId;
		private Instant barrierTimestamp;
		private Instant reachedAt;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withBarrierId(String value) {
			barrierId = value;
			return this;
		}

		public Builder withBarrierTimestamp(Instant value) {
			barrierTimestamp = value;
			return this;
		}

		public Builder withReachedAt(Instant value) {
			reachedAt = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateIndexerLoadBarrier build() {
			return new UpdateIndexerLoadBarrier(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(barrierId, "barrierId"),
				Objects.requireNonNull(barrierTimestamp, "barrierTimestamp"),
				Objects.requireNonNull(reachedAt, "reachedAt"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
