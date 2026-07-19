package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.provisioning.ManifestStatus;

public record UpdateManifestStatus(
	Integer id,
	ManifestStatus status,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private ManifestStatus status;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withStatus(ManifestStatus value) {
			status = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateManifestStatus build() {
			return new UpdateManifestStatus(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(status, "status"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
