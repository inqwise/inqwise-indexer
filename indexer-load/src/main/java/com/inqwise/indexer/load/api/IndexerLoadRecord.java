package com.inqwise.indexer.load.api;

import java.time.Instant;
import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record IndexerLoadRecord(
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
	boolean reviewRequired,
	Instant approvedAt,
	String approvedBy,
	String approvalReason,
	String lastBarrierId,
	Instant lastBarrierTimestamp,
	Instant lastBarrierReachedAt,
	String failureReason,
	Instant failedAt,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public IndexerLoadRecord {
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
		private Instant approvedAt;
		private String approvedBy;
		private String approvalReason;
		private String lastBarrierId;
		private Instant lastBarrierTimestamp;
		private Instant lastBarrierReachedAt;
		private String failureReason;
		private Instant failedAt;
		private Instant createdAt;
		private Instant updatedAt;
		private long version;

		private Builder() {
		}

		public Builder from(IndexerLoadRecord value) {
			Objects.requireNonNull(value, "value");
			indexerId = value.indexerId();
			targetId = value.targetId();
			liveIndexerId = value.liveIndexerId();
			liveWriterPolicy = value.liveWriterPolicy();
			providerId = value.providerId();
			state = value.state();
			reloadStartAt = value.reloadStartAt();
			liveReplayFrom = value.liveReplayFrom();
			sourceFrom = value.sourceFrom();
			sourceTo = value.sourceTo();
			sourceQuery = value.sourceQuery();
			sourcePlaybookId = value.sourcePlaybookId();
			reviewRequired = value.reviewRequired();
			approvedAt = value.approvedAt();
			approvedBy = value.approvedBy();
			approvalReason = value.approvalReason();
			lastBarrierId = value.lastBarrierId();
			lastBarrierTimestamp = value.lastBarrierTimestamp();
			lastBarrierReachedAt = value.lastBarrierReachedAt();
			failureReason = value.failureReason();
			failedAt = value.failedAt();
			createdAt = value.createdAt();
			updatedAt = value.updatedAt();
			version = value.version();
			return this;
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

		public Builder withApprovedAt(Instant value) {
			approvedAt = value;
			return this;
		}

		public Builder withApprovedBy(String value) {
			approvedBy = value;
			return this;
		}

		public Builder withApprovalReason(String value) {
			approvalReason = value;
			return this;
		}

		public Builder withLastBarrierId(String value) {
			lastBarrierId = value;
			return this;
		}

		public Builder withLastBarrierTimestamp(Instant value) {
			lastBarrierTimestamp = value;
			return this;
		}

		public Builder withLastBarrierReachedAt(Instant value) {
			lastBarrierReachedAt = value;
			return this;
		}

		public Builder withFailureReason(String value) {
			failureReason = value;
			return this;
		}

		public Builder withFailedAt(Instant value) {
			failedAt = value;
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

		public IndexerLoadRecord build() {
			return new IndexerLoadRecord(
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
				reviewRequired,
				approvedAt,
				approvedBy,
				approvalReason,
				lastBarrierId,
				lastBarrierTimestamp,
				lastBarrierReachedAt,
				failureReason,
				failedAt,
				createdAt,
				updatedAt,
				version
			);
		}
	}
}
