package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

public record InsertIndexer(
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
	MutationState mutationState
) {
	public InsertIndexer {
		Objects.requireNonNull(prefix, "prefix");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(indexOwnership, "indexOwnership");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
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

		private Builder() {
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

		public InsertIndexer build() {
			return new InsertIndexer(
				Objects.requireNonNull(prefix, "prefix"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(targetName, "targetName"),
				Objects.requireNonNull(indexName, "indexName"),
				Objects.requireNonNull(queueName, "queueName"),
				Objects.requireNonNull(type, "type"),
				Objects.requireNonNull(role, "role"),
				Objects.requireNonNull(indexOwnership, "indexOwnership"),
				Objects.requireNonNull(status, "status"),
				Objects.requireNonNull(provisioningState, "provisioningState"),
				Objects.requireNonNull(runtimeState, "runtimeState"),
				Objects.requireNonNull(publicationState, "publicationState"),
				Objects.requireNonNull(mutationState, "mutationState")
			);
		}
	}
}
