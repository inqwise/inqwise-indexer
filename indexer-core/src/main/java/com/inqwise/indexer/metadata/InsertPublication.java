package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.publication.ReadinessState;

public record InsertPublication(
	String prefix,
	Integer indexerId,
	Integer targetId,
	String targetName,
	String indexName,
	ReadinessState readinessState,
	String reason
) {
	public InsertPublication {
		Objects.requireNonNull(prefix, "prefix");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String prefix;
		private Integer indexerId;
		private Integer targetId;
		private String targetName;
		private String indexName;
		private ReadinessState readinessState;
		private String reason;

		private Builder() {
		}

		public Builder withPrefix(String value) {
			prefix = value;
			return this;
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
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

		public InsertPublication build() {
			return new InsertPublication(
				Objects.requireNonNull(prefix, "prefix"),
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(targetName, "targetName"),
				Objects.requireNonNull(indexName, "indexName"),
				Objects.requireNonNull(readinessState, "readinessState"),
				reason
			);
		}
	}
}
