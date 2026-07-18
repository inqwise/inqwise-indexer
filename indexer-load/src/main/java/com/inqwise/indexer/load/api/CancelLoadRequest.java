package com.inqwise.indexer.load.api;

import java.util.Objects;

public record CancelLoadRequest(Integer indexerId, String reason, long expectedVersion) {
	public CancelLoadRequest {
		Objects.requireNonNull(indexerId, "indexerId");
		if (expectedVersion < 0) {
			throw new IllegalArgumentException("expectedVersion must not be negative");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private String reason;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withReason(String value) {
			reason = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public CancelLoadRequest build() {
			return new CancelLoadRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				reason,
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
