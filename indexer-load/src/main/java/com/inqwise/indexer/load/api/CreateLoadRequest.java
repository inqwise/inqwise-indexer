package com.inqwise.indexer.load.api;

import java.time.Instant;
import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record CreateLoadRequest(
	String providerId,
	Integer targetId,
	LiveWriterPolicy liveWriterPolicy,
	Instant reloadStartAt,
	Instant liveReplayFrom,
	Instant sourceFrom,
	Instant sourceTo,
	JsonObject sourceQuery,
	String sourcePlaybookId,
	boolean reviewRequired
) {
	public CreateLoadRequest {
		Objects.requireNonNull(providerId, "providerId");
		Objects.requireNonNull(targetId, "targetId");
		liveWriterPolicy = liveWriterPolicy == null ? LiveWriterPolicy.NONE : liveWriterPolicy;
		sourceQuery = sourceQuery == null ? null : sourceQuery.copy();
	}

	@Override
	public JsonObject sourceQuery() {
		return sourceQuery == null ? null : sourceQuery.copy();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String providerId;
		private Integer targetId;
		private LiveWriterPolicy liveWriterPolicy;
		private Instant reloadStartAt;
		private Instant liveReplayFrom;
		private Instant sourceFrom;
		private Instant sourceTo;
		private JsonObject sourceQuery;
		private String sourcePlaybookId;
		private boolean reviewRequired;

		private Builder() {
		}

		public Builder withProviderId(String value) {
			providerId = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withLiveWriterPolicy(LiveWriterPolicy value) {
			liveWriterPolicy = value;
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
			sourceQuery = value == null ? null : value.copy();
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

		public CreateLoadRequest build() {
			return new CreateLoadRequest(
				providerId,
				targetId,
				liveWriterPolicy,
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
