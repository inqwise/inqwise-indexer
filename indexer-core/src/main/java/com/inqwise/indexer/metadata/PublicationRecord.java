package com.inqwise.indexer.metadata;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.publication.ReadinessState;

public record PublicationRecord(
	Integer id,
	String prefix,
	Integer indexerId,
	Integer targetId,
	String targetName,
	String indexName,
	ReadinessState readinessState,
	String reason,
	Instant readyAt,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public static Builder builder() {
		return new Builder();
	}

	public String uid() {
		return MetadataUid.toToken(prefix, id);
	}

	public static final class Builder {
		private Integer id;
		private String prefix;
		private Integer indexerId;
		private Integer targetId;
		private String targetName;
		private String indexName;
		private ReadinessState readinessState;
		private String reason;
		private Instant readyAt;
		private Instant createdAt;
		private Instant updatedAt;
		private Long version;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
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

		public Builder withReadyAt(Instant value) {
			readyAt = value;
			return this;
		}

		public Builder withCreatedAt(Instant value) {
			createdAt = value;
			return this;
		}

		public Builder withUpdatedAt(Instant value) {
			updatedAt = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public PublicationRecord build() {
			return new PublicationRecord(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(prefix, "prefix"),
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(targetName, "targetName"),
				Objects.requireNonNull(indexName, "indexName"),
				Objects.requireNonNull(readinessState, "readinessState"),
				reason,
				readyAt,
				Objects.requireNonNull(createdAt, "createdAt"),
				Objects.requireNonNull(updatedAt, "updatedAt"),
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
