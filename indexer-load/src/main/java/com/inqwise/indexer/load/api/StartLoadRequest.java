package com.inqwise.indexer.load.api;

import java.util.Objects;

public record StartLoadRequest(
	Integer indexerId,
	long expectedVersion
) {
	public StartLoadRequest {
		Objects.requireNonNull(indexerId, "indexerId");
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

		public StartLoadRequest build() {
			return new StartLoadRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
