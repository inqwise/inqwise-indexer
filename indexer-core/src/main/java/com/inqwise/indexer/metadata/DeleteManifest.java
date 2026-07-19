package com.inqwise.indexer.metadata;

import java.util.Objects;

public record DeleteManifest(
	Integer id,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public DeleteManifest build() {
			return new DeleteManifest(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
