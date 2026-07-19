package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.publication.ReadinessState;

public record UpdatePublicationReadiness(
	Integer id,
	ReadinessState readinessState,
	String reason,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private ReadinessState readinessState;
		private String reason;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withReadinessState(ReadinessState value) {
			readinessState = value;
			return this;
		}

		public Builder withReason(String value) {
			reason = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdatePublicationReadiness build() {
			return new UpdatePublicationReadiness(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(readinessState, "readinessState"),
				reason,
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
