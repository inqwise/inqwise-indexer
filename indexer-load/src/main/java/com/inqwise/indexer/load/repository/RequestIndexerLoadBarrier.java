package com.inqwise.indexer.load.repository;

import java.time.Instant;
import java.util.Objects;

public record RequestIndexerLoadBarrier(
	Integer indexerId,
	String barrierId,
	Instant barrierTimestamp,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private String barrierId;
		private Instant barrierTimestamp;
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

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public RequestIndexerLoadBarrier build() {
			return new RequestIndexerLoadBarrier(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(barrierId, "barrierId"),
				Objects.requireNonNull(barrierTimestamp, "barrierTimestamp"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
