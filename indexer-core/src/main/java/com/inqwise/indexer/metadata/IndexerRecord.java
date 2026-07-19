package com.inqwise.indexer.metadata;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

public record IndexerRecord(
	Integer id,
	String prefix,
	Integer targetId,
	String targetName,
	String indexName,
	String queueName,
	IndexerType type,
	IndexerRole role,
	IndexResourceOwnership indexOwnership,
	IndexerStatus status,
	IndexerProvisioningState provisioningState,
	IndexerRuntimeState runtimeState,
	PublicationState publicationState,
	MutationState mutationState,
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
		private Integer targetId;
		private String targetName;
		private String indexName;
		private String queueName;
		private IndexerType type;
		private IndexerRole role;
		private IndexResourceOwnership indexOwnership;
		private IndexerStatus status;
		private IndexerProvisioningState provisioningState;
		private IndexerRuntimeState runtimeState;
		private PublicationState publicationState;
		private MutationState mutationState;
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

		public Builder withQueueName(String value) {
			queueName = value;
			return this;
		}

		public Builder withType(IndexerType value) {
			type = value;
			return this;
		}

		public Builder withRole(IndexerRole value) {
			role = value;
			return this;
		}

		public Builder withIndexOwnership(IndexResourceOwnership value) {
			indexOwnership = value;
			return this;
		}

		public Builder withStatus(IndexerStatus value) {
			status = value;
			return this;
		}

		public Builder withProvisioningState(IndexerProvisioningState value) {
			provisioningState = value;
			return this;
		}

		public Builder withRuntimeState(IndexerRuntimeState value) {
			runtimeState = value;
			return this;
		}

		public Builder withPublicationState(PublicationState value) {
			publicationState = value;
			return this;
		}

		public Builder withMutationState(MutationState value) {
			mutationState = value;
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

		public IndexerRecord build() {
			return new IndexerRecord(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(prefix, "prefix"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(targetName, "targetName"),
				Objects.requireNonNull(indexName, "indexName"),
				queueName,
				Objects.requireNonNull(type, "type"),
				Objects.requireNonNull(role, "role"),
				Objects.requireNonNull(indexOwnership, "indexOwnership"),
				Objects.requireNonNull(status, "status"),
				Objects.requireNonNull(provisioningState, "provisioningState"),
				Objects.requireNonNull(runtimeState, "runtimeState"),
				Objects.requireNonNull(publicationState, "publicationState"),
				Objects.requireNonNull(mutationState, "mutationState"),
				Objects.requireNonNull(createdAt, "createdAt"),
				Objects.requireNonNull(updatedAt, "updatedAt"),
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
