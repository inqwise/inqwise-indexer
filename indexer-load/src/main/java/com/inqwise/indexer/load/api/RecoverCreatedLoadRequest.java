package com.inqwise.indexer.load.api;

import java.util.Objects;

public record RecoverCreatedLoadRequest(Integer indexerId, long expectedVersion) {
	public RecoverCreatedLoadRequest {
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
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public RecoverCreatedLoadRequest build() {
			return new RecoverCreatedLoadRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
