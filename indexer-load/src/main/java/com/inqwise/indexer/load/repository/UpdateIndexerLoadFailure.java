package com.inqwise.indexer.load.repository;

import java.time.Instant;
import java.util.Objects;

public record UpdateIndexerLoadFailure(
	Integer indexerId,
	String failureReason,
	Instant failedAt,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private String failureReason;
		private Instant failedAt;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withFailureReason(String value) {
			failureReason = value;
			return this;
		}

		public Builder withFailedAt(Instant value) {
			failedAt = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateIndexerLoadFailure build() {
			return new UpdateIndexerLoadFailure(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(failureReason, "failureReason"),
				failedAt,
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
