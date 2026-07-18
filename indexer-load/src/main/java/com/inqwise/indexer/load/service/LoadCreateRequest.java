package com.inqwise.indexer.load.service;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.load.api.CreateLoadRequest;
import com.inqwise.indexer.load.api.LiveWriterPolicy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class LoadCreateRequest {
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

	public LoadCreateRequest() {
	}

	public LoadCreateRequest(JsonObject json) {
		providerId = json.getString("provider_id");
		targetId = json.getInteger("target_id");
		String policy = json.getString("live_writer_policy");
		liveWriterPolicy = policy == null ? null : LiveWriterPolicy.valueOf(policy);
		reloadStartAt = instant(json, "reload_start_at");
		liveReplayFrom = instant(json, "live_replay_from");
		sourceFrom = instant(json, "source_from");
		sourceTo = instant(json, "source_to");
		sourceQuery = copy(json.getJsonObject("source_query"));
		sourcePlaybookId = json.getString("source_playbook_id");
		reviewRequired = json.getBoolean("review_required", false);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("provider_id", providerId)
			.put("target_id", targetId)
			.put("live_writer_policy", liveWriterPolicy == null ? null : liveWriterPolicy.name())
			.put("reload_start_at", string(reloadStartAt))
			.put("live_replay_from", string(liveReplayFrom))
			.put("source_from", string(sourceFrom))
			.put("source_to", string(sourceTo))
			.put("source_query", copy(sourceQuery))
			.put("source_playbook_id", sourcePlaybookId)
			.put("review_required", reviewRequired);
	}

	CreateLoadRequest toDomainRequest() {
		return CreateLoadRequest.builder()
			.withProviderId(providerId)
			.withTargetId(targetId)
			.withLiveWriterPolicy(liveWriterPolicy)
			.withReloadStartAt(reloadStartAt)
			.withLiveReplayFrom(liveReplayFrom)
			.withSourceFrom(sourceFrom)
			.withSourceTo(sourceTo)
			.withSourceQuery(sourceQuery)
			.withSourcePlaybookId(sourcePlaybookId)
			.withReviewRequired(reviewRequired)
			.build();
	}

	private static Instant instant(JsonObject json, String key) {
		String value = json.getString(key);
		return value == null ? null : Instant.parse(value);
	}

	private static String string(Instant value) {
		return value == null ? null : value.toString();
	}

	private static JsonObject copy(JsonObject value) {
		return value == null ? null : value.copy();
	}

	public String getProviderId() {
		return providerId;
	}

	public LoadCreateRequest setProviderId(String value) {
		providerId = value;
		return this;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public LoadCreateRequest setTargetId(Integer value) {
		targetId = value;
		return this;
	}

	public LiveWriterPolicy getLiveWriterPolicy() {
		return liveWriterPolicy;
	}

	public LoadCreateRequest setLiveWriterPolicy(LiveWriterPolicy value) {
		liveWriterPolicy = value;
		return this;
	}

	public Instant getReloadStartAt() {
		return reloadStartAt;
	}

	public LoadCreateRequest setReloadStartAt(Instant value) {
		reloadStartAt = value;
		return this;
	}

	public Instant getLiveReplayFrom() {
		return liveReplayFrom;
	}

	public LoadCreateRequest setLiveReplayFrom(Instant value) {
		liveReplayFrom = value;
		return this;
	}

	public Instant getSourceFrom() {
		return sourceFrom;
	}

	public LoadCreateRequest setSourceFrom(Instant value) {
		sourceFrom = value;
		return this;
	}

	public Instant getSourceTo() {
		return sourceTo;
	}

	public LoadCreateRequest setSourceTo(Instant value) {
		sourceTo = value;
		return this;
	}

	public JsonObject getSourceQuery() {
		return copy(sourceQuery);
	}

	public LoadCreateRequest setSourceQuery(JsonObject value) {
		sourceQuery = copy(value);
		return this;
	}

	public String getSourcePlaybookId() {
		return sourcePlaybookId;
	}

	public LoadCreateRequest setSourcePlaybookId(String value) {
		sourcePlaybookId = value;
		return this;
	}

	public boolean isReviewRequired() {
		return reviewRequired;
	}

	public LoadCreateRequest setReviewRequired(boolean value) {
		reviewRequired = value;
		return this;
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

		public Builder fromJson(JsonObject json) {
			Objects.requireNonNull(json, "json");
			providerId = json.getString("provider_id");
			targetId = json.getInteger("target_id");
			String policy = json.getString("live_writer_policy");
			liveWriterPolicy = policy == null ? null : LiveWriterPolicy.valueOf(policy);
			reloadStartAt = instant(json, "reload_start_at");
			liveReplayFrom = instant(json, "live_replay_from");
			sourceFrom = instant(json, "source_from");
			sourceTo = instant(json, "source_to");
			sourceQuery = copy(json.getJsonObject("source_query"));
			sourcePlaybookId = json.getString("source_playbook_id");
			reviewRequired = json.getBoolean("review_required", false);
			return this;
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

		public LoadCreateRequest build() {
			return new LoadCreateRequest()
				.setProviderId(Objects.requireNonNull(providerId, "providerId"))
				.setTargetId(Objects.requireNonNull(targetId, "targetId"))
				.setLiveWriterPolicy(liveWriterPolicy)
				.setReloadStartAt(reloadStartAt)
				.setLiveReplayFrom(liveReplayFrom)
				.setSourceFrom(sourceFrom)
				.setSourceTo(sourceTo)
				.setSourceQuery(sourceQuery)
				.setSourcePlaybookId(sourcePlaybookId)
				.setReviewRequired(reviewRequired);
		}
	}
}
