package com.inqwise.indexer.load.repository;

import java.util.Objects;

public record AttachLiveWriterRequest(
	Integer indexerId,
	Integer liveIndexerId,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private Integer liveIndexerId;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withLiveIndexerId(Integer value) {
			liveIndexerId = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public AttachLiveWriterRequest build() {
			return new AttachLiveWriterRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(liveIndexerId, "liveIndexerId"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
