package com.inqwise.indexer.catalog.indexers;

import java.time.Instant;
import java.util.Objects;

public record IndexerCatalogEntry(
	Integer id,
	String uid,
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
	MutationState mutationState,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public IndexerCatalogEntry {
		id = Objects.requireNonNull(id, "id");
		uid = Objects.requireNonNull(uid, "uid");
		targetId = Objects.requireNonNull(targetId, "targetId");
		targetName = Objects.requireNonNull(targetName, "targetName");
		indexName = Objects.requireNonNull(indexName, "indexName");
		queueName = Objects.requireNonNull(queueName, "queueName");
		type = Objects.requireNonNull(type, "type");
		role = Objects.requireNonNull(role, "role");
		indexOwnership = Objects.requireNonNull(indexOwnership, "indexOwnership");
		status = Objects.requireNonNull(status, "status");
		provisioningState = Objects.requireNonNull(provisioningState, "provisioningState");
		runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
		mutationState = Objects.requireNonNull(mutationState, "mutationState");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private String uid;
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
		private MutationState mutationState;
		private Instant createdAt;
		private Instant updatedAt;
		private long version;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withUid(String value) {
			uid = value;
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

		public IndexerCatalogEntry build() {
			return new IndexerCatalogEntry(
				id,
				uid,
				targetId,
				targetName,
				indexName,
				queueName,
				type,
				role,
				indexOwnership,
				status,
				provisioningState,
				runtimeState,
				mutationState,
				createdAt,
				updatedAt,
				version
			);
		}
	}
}
