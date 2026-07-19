package com.inqwise.indexer.load.api;

import java.time.Instant;
import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record LoadRequest(
	Integer indexerId,
	Integer targetId,
	Integer liveIndexerId,
	String providerId,
	String targetName,
	String indexName,
	String queueName,
	Instant reloadStartAt,
	Instant liveReplayFrom,
	Instant sourceFrom,
	Instant sourceTo,
	JsonObject sourceQuery,
	String sourcePlaybookId
) {
	public LoadRequest {
		sourceQuery = copy(sourceQuery);
	}

	@Override
	public JsonObject sourceQuery() {
		return copy(sourceQuery);
	}

	public static Builder builder() {
		return new Builder();
	}

	private static JsonObject copy(JsonObject value) {
		return value == null ? null : value.copy();
	}

	public static final class Builder {
		private Integer indexerId;
		private Integer targetId;
		private Integer liveIndexerId;
		private String providerId;
		private String targetName;
		private String indexName;
		private String queueName;
		private Instant reloadStartAt;
		private Instant liveReplayFrom;
		private Instant sourceFrom;
		private Instant sourceTo;
		private JsonObject sourceQuery;
		private String sourcePlaybookId;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withLiveIndexerId(Integer value) {
			liveIndexerId = value;
			return this;
		}

		public Builder withProviderId(String value) {
			providerId = value;
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

		public Builder withReloadStartAt(Instant value) {
			reloadStartAt = value;
			return this;
		}

		public Builder withLiveReplayFrom(Instant value) {
			liveReplayFrom = value;
			return this;
		}

		public Builder withSourceFrom(Instant value) {
			sourceFrom = value;
			return this;
		}

		public Builder withSourceTo(Instant value) {
			sourceTo = value;
			return this;
		}

		public Builder withSourceQuery(JsonObject value) {
			sourceQuery = copy(value);
			return this;
		}

		public Builder withSourcePlaybookId(String value) {
			sourcePlaybookId = value;
			return this;
		}

		public LoadRequest build() {
			return new LoadRequest(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetId, "targetId"),
				liveIndexerId,
				Objects.requireNonNull(providerId, "providerId"),
				Objects.requireNonNull(targetName, "targetName"),
				Objects.requireNonNull(indexName, "indexName"),
				Objects.requireNonNull(queueName, "queueName"),
				reloadStartAt,
				liveReplayFrom,
				sourceFrom,
				sourceTo,
				sourceQuery,
				sourcePlaybookId
			);
		}
	}
}
