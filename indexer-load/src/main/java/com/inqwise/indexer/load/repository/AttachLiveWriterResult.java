package com.inqwise.indexer.load.repository;

import java.util.Objects;

public record AttachLiveWriterResult(
	boolean attached,
	Integer liveIndexerId,
	long version
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private boolean attached;
		private Integer liveIndexerId;
		private long version;

		private Builder() {
		}

		public Builder withAttached(boolean value) {
			attached = value;
			return this;
		}

		public Builder withLiveIndexerId(Integer value) {
			liveIndexerId = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public AttachLiveWriterResult build() {
			return new AttachLiveWriterResult(
				attached,
				Objects.requireNonNull(liveIndexerId, "liveIndexerId"),
				version
			);
		}
	}
}
