package com.inqwise.indexer.publication;

import java.util.Objects;

public record PublishIndexRequest(Integer indexerId, long expectedVersion) {
	public PublishIndexRequest {
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

		public PublishIndexRequest build() {
			return new PublishIndexRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
