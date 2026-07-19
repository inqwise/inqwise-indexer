package com.inqwise.indexer.publication;

import java.util.Objects;

public record IndexPublicationResult(
	Integer indexerId,
	Integer targetId,
	PublicationState publicationState,
	long version
) {
	public IndexPublicationResult {
		indexerId = Objects.requireNonNull(indexerId, "indexerId");
		targetId = Objects.requireNonNull(targetId, "targetId");
		publicationState = Objects.requireNonNull(publicationState, "publicationState");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private Integer targetId;
		private PublicationState publicationState;
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

		public Builder withPublicationState(PublicationState value) {
			publicationState = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public IndexPublicationResult build() {
			return new IndexPublicationResult(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(publicationState, "publicationState"),
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
