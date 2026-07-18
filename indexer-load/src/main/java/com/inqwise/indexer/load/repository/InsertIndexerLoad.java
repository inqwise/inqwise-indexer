package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;


import java.time.Instant;
import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record InsertIndexerLoad(
	Integer indexerId,
	Integer targetId,
	Integer liveIndexerId,
	LiveWriterPolicy liveWriterPolicy,
	String providerId,
	IndexerLoadState state,
	Instant reloadStartAt,
	Instant liveReplayFrom,
	Instant sourceFrom,
	Instant sourceTo,
	JsonObject sourceQuery,
	String sourcePlaybookId,
	boolean reviewRequired
) {
	public InsertIndexerLoad {
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
		private LiveWriterPolicy liveWriterPolicy;
		private String providerId;
		private IndexerLoadState state;
		private Instant reloadStartAt;
		private Instant liveReplayFrom;
		private Instant sourceFrom;
		private Instant sourceTo;
		private JsonObject sourceQuery;
		private String sourcePlaybookId;
		private boolean reviewRequired;

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

		public Builder withLiveWriterPolicy(LiveWriterPolicy value) {
			liveWriterPolicy = value;
			return this;
		}

		public Builder withProviderId(String value) {
			providerId = value;
			return this;
		}

		public Builder withState(IndexerLoadState value) {
			state = value;
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

		public Builder withReviewRequired(boolean value) {
			reviewRequired = value;
			return this;
		}

		public InsertIndexerLoad build() {
			return new InsertIndexerLoad(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetId, "targetId"),
				liveIndexerId,
				Objects.requireNonNull(liveWriterPolicy, "liveWriterPolicy"),
				Objects.requireNonNull(providerId, "providerId"),
				Objects.requireNonNull(state, "state"),
				reloadStartAt,
				liveReplayFrom,
				sourceFrom,
				sourceTo,
				sourceQuery,
				sourcePlaybookId,
				reviewRequired
			);
		}
	}
}
