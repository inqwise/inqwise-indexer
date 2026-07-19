package com.inqwise.indexer.provisioning;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;

public record CreateIndexerProvisioningRequest(
	String prefix,
	Integer targetId,
	String indexName,
	String queueName,
	IndexerRole role,
	IndexResourceOwnership indexOwnership,
	IndexerRuntimeState runtimeState
) {
	public CreateIndexerProvisioningRequest {
		prefix = requireNonBlank(prefix, "prefix");
		targetId = Objects.requireNonNull(targetId, "targetId");
		indexName = DocumentIndexNameValidator.requireConcrete(indexName);
		queueName = requireNonBlank(queueName, "queueName");
		role = Objects.requireNonNull(role, "role");
		indexOwnership = Objects.requireNonNull(indexOwnership, "indexOwnership");
		runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
	}

	public static Builder builder() {
		return new Builder();
	}

	private static String requireNonBlank(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}

	public static final class Builder {
		private String prefix;
		private Integer targetId;
		private String indexName;
		private String queueName;
		private IndexerRole role;
		private IndexResourceOwnership indexOwnership;
		private IndexerRuntimeState runtimeState;

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

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withQueueName(String value) {
			queueName = value;
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

		public Builder withRuntimeState(IndexerRuntimeState value) {
			runtimeState = value;
			return this;
		}

		public CreateIndexerProvisioningRequest build() {
			return new CreateIndexerProvisioningRequest(
				prefix,
				targetId,
				indexName,
				queueName,
				role,
				indexOwnership,
				runtimeState
			);
		}
	}
}
