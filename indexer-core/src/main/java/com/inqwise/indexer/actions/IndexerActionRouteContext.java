package com.inqwise.indexer.actions;

import com.inqwise.indexer.catalog.indexers.IndexerRole;

import java.util.Objects;

public record IndexerActionRouteContext(
	Integer targetId,
	Integer indexerId,
	String targetName,
	String indexName,
	String queueName,
	IndexerRole role
) {
	public IndexerActionRouteContext {
		targetId = ActionItemValidation.requiredPositive(targetId, "targetId");
		indexerId = ActionItemValidation.requiredPositive(indexerId, "indexerId");
		indexName = ActionItemValidation.requiredText(indexName, "indexName");
		queueName = ActionItemValidation.requiredText(queueName, "queueName");
		Objects.requireNonNull(role, "role");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private Integer indexerId;
		private String targetName;
		private String indexName;
		private String queueName;
		private IndexerRole role;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
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

		public Builder withRole(IndexerRole value) {
			role = value;
			return this;
		}

		public IndexerActionRouteContext build() {
			return new IndexerActionRouteContext(
				targetId,
				indexerId,
				targetName,
				indexName,
				queueName,
				role
			);
		}
	}
}
