package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.publication.PublicationState;

public record UpdateIndexerPublicationState(
	Integer id,
	PublicationState publicationState,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private PublicationState publicationState;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withPublicationState(PublicationState value) {
			publicationState = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateIndexerPublicationState build() {
			return new UpdateIndexerPublicationState(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(publicationState, "publicationState"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
