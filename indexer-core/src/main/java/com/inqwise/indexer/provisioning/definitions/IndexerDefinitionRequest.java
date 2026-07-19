package com.inqwise.indexer.provisioning.definitions;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerType;

public record IndexerDefinitionRequest(
	Integer targetId,
	String targetName,
	IndexerType indexerType,
	IndexerRole role,
	IndexResourceOwnership indexOwnership
) {
	public IndexerDefinitionRequest {
		targetId = Objects.requireNonNull(targetId, "targetId");
		targetName = Objects.requireNonNull(targetName, "targetName");
		indexerType = Objects.requireNonNull(indexerType, "indexerType");
		role = Objects.requireNonNull(role, "role");
		indexOwnership = Objects.requireNonNull(indexOwnership, "indexOwnership");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private String targetName;
		private IndexerType indexerType;
		private IndexerRole role;
		private IndexResourceOwnership indexOwnership;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withIndexerType(IndexerType value) {
			indexerType = value;
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

		public IndexerDefinitionRequest build() {
			return new IndexerDefinitionRequest(
				targetId,
				targetName,
				indexerType,
				role,
				indexOwnership
			);
		}
	}
}
