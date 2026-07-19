package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.catalog.targets.TargetStatus;

public record UpdateTargetStatus(
	Integer id,
	TargetStatus status,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private TargetStatus status;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withStatus(TargetStatus value) {
			status = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateTargetStatus build() {
			return new UpdateTargetStatus(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(status, "status"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
