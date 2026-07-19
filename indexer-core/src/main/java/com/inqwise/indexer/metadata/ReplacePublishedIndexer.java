package com.inqwise.indexer.metadata;

import java.util.Objects;

public record ReplacePublishedIndexer(
	Integer targetId,
	Integer candidateIndexerId,
	long expectedCandidateVersion,
	Integer previousIndexerId,
	Long expectedPreviousVersion,
	Integer ownershipSourceIndexerId,
	Long expectedOwnershipSourceVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private Integer candidateIndexerId;
		private Long expectedCandidateVersion;
		private Integer previousIndexerId;
		private Long expectedPreviousVersion;
		private Integer ownershipSourceIndexerId;
		private Long expectedOwnershipSourceVersion;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withCandidateIndexerId(Integer value) {
			candidateIndexerId = value;
			return this;
		}

		public Builder withExpectedCandidateVersion(long value) {
			expectedCandidateVersion = value;
			return this;
		}

		public Builder withPreviousIndexerId(Integer value) {
			previousIndexerId = value;
			return this;
		}

		public Builder withExpectedPreviousVersion(Long value) {
			expectedPreviousVersion = value;
			return this;
		}

		public Builder withOwnershipSourceIndexerId(Integer value) {
			ownershipSourceIndexerId = value;
			return this;
		}

		public Builder withExpectedOwnershipSourceVersion(Long value) {
			expectedOwnershipSourceVersion = value;
			return this;
		}

		public ReplacePublishedIndexer build() {
			return new ReplacePublishedIndexer(
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(candidateIndexerId, "candidateIndexerId"),
				Objects.requireNonNull(expectedCandidateVersion, "expectedCandidateVersion"),
				previousIndexerId,
				expectedPreviousVersion,
				ownershipSourceIndexerId,
				expectedOwnershipSourceVersion
			);
		}
	}
}
