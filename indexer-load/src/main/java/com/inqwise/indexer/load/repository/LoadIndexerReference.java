package com.inqwise.indexer.load.repository;

import java.util.Objects;

public record LoadIndexerReference(
	Integer id,
	Integer targetId,
	Long version
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private Integer targetId;
		private Long version;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withVersion(Long value) {
			version = value;
			return this;
		}

		public LoadIndexerReference build() {
			return new LoadIndexerReference(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
