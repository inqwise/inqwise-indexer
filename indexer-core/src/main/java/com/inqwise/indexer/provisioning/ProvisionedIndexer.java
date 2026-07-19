package com.inqwise.indexer.provisioning;

import java.util.Objects;

public record ProvisionedIndexer(
	Integer indexerId,
	Integer targetId,
	long version
) {
	public ProvisionedIndexer {
		indexerId = Objects.requireNonNull(indexerId, "indexerId");
		targetId = Objects.requireNonNull(targetId, "targetId");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private Integer targetId;
		private Long version;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public ProvisionedIndexer build() {
			return new ProvisionedIndexer(
				indexerId,
				targetId,
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
