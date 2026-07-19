package com.inqwise.indexer.publication;

import java.util.Objects;

public record PublicationReadinessResult(
	Integer publicationId,
	Integer indexerId,
	ReadinessState readinessState,
	long version
) {
	public PublicationReadinessResult {
		publicationId = Objects.requireNonNull(publicationId, "publicationId");
		indexerId = Objects.requireNonNull(indexerId, "indexerId");
		readinessState = Objects.requireNonNull(readinessState, "readinessState");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer publicationId;
		private Integer indexerId;
		private ReadinessState readinessState;
		private Long version;

		private Builder() {
		}

		public Builder withPublicationId(Integer value) {
			publicationId = value;
			return this;
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withReadinessState(ReadinessState value) {
			readinessState = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public PublicationReadinessResult build() {
			return new PublicationReadinessResult(
				Objects.requireNonNull(publicationId, "publicationId"),
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(readinessState, "readinessState"),
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
