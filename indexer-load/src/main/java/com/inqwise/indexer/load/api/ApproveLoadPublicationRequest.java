package com.inqwise.indexer.load.api;

import java.time.Instant;
import java.util.Objects;

public record ApproveLoadPublicationRequest(
	Integer indexerId,
	Instant approvedAt,
	String approvedBy,
	String approvalReason,
	long expectedVersion
) {
	public ApproveLoadPublicationRequest {
		Objects.requireNonNull(indexerId, "indexerId");
		if (expectedVersion < 0) {
			throw new IllegalArgumentException("expectedVersion must not be negative");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private Instant approvedAt;
		private String approvedBy;
		private String approvalReason;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withApprovedAt(Instant value) {
			approvedAt = value;
			return this;
		}

		public Builder withApprovedBy(String value) {
			approvedBy = value;
			return this;
		}

		public Builder withApprovalReason(String value) {
			approvalReason = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public ApproveLoadPublicationRequest build() {
			return new ApproveLoadPublicationRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				approvedAt,
				approvedBy,
				approvalReason,
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
