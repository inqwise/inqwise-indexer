package com.inqwise.indexer.publication;

import java.util.Objects;

public record MarkIndexReadyRequest(
	Integer publicationId,
	String reason,
	long expectedVersion
) {
	public MarkIndexReadyRequest {
		Objects.requireNonNull(publicationId, "publicationId");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer publicationId;
		private String reason;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withPublicationId(Integer value) {
			publicationId = value;
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

		public MarkIndexReadyRequest build() {
			return new MarkIndexReadyRequest(
				Objects.requireNonNull(publicationId, "publicationId"),
				reason,
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
