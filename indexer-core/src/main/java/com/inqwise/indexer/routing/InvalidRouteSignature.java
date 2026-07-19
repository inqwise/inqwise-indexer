package com.inqwise.indexer.routing;

import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionType;

public record InvalidRouteSignature(
	String targetName,
	String periodKey,
	Integer targetId,
	Integer indexerId,
	String indexName,
	IndexerActionType actionType
) {
	public InvalidRouteSignature {
		Objects.requireNonNull(actionType, "actionType");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String targetName;
		private String periodKey;
		private Integer targetId;
		private Integer indexerId;
		private String indexName;
		private IndexerActionType actionType;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withPeriodKey(String value) {
			periodKey = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withActionType(IndexerActionType value) {
			actionType = value;
			return this;
		}

		public InvalidRouteSignature build() {
			return new InvalidRouteSignature(
				targetName,
				periodKey,
				targetId,
				indexerId,
				indexName,
				Objects.requireNonNull(actionType, "actionType")
			);
		}
	}
}
