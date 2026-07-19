package com.inqwise.indexer.service.action;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetActionSubmitRequest {
	public static final class Keys {
		public static final String SUBMISSION_ID = "submission_id";
		public static final String TARGET_NAME = "target_name";
		public static final String TIMESTAMP = "timestamp";
		public static final String ACTIONS = "actions";

		private Keys() {
		}
	}

	private String submissionId;
	private String targetName;
	private Instant timestamp;
	private List<IndexerActionItem> actions = List.of();

	public TargetActionSubmitRequest() {
	}

	public TargetActionSubmitRequest(JsonObject json) {
		this.submissionId = json.getString(Keys.SUBMISSION_ID);
		this.targetName = json.getString(Keys.TARGET_NAME);
		this.timestamp = json.getString(Keys.TIMESTAMP) == null
			? null
			: Instant.parse(json.getString(Keys.TIMESTAMP));
		this.actions = json.getJsonArray(Keys.ACTIONS, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(IndexerActionItem::fromJson)
			.toList();
	}

	public static TargetActionSubmitRequest fromJson(JsonObject json) {
		return new TargetActionSubmitRequest(Objects.requireNonNull(json, "json"));
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put(Keys.ACTIONS, new JsonArray(actions.stream()
				.map(IndexerActionItem::toJson)
				.toList()));

		if (submissionId != null) {
			json.put(Keys.SUBMISSION_ID, submissionId);
		}

		if (targetName != null) {
			json.put(Keys.TARGET_NAME, targetName);
		}

		if (timestamp != null) {
			json.put(Keys.TIMESTAMP, timestamp.toString());
		}

		return json;
	}

	public String getSubmissionId() {
		return submissionId;
	}

	public TargetActionSubmitRequest setSubmissionId(String submissionId) {
		this.submissionId = submissionId;
		return this;
	}

	public String getTargetName() {
		return targetName;
	}

	public TargetActionSubmitRequest setTargetName(String targetName) {
		this.targetName = targetName;
		return this;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public TargetActionSubmitRequest setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
		return this;
	}

	public List<IndexerActionItem> getActions() {
		return actions;
	}

	public TargetActionSubmitRequest setActions(List<IndexerActionItem> actions) {
		this.actions = actions == null ? List.of() : List.copyOf(actions);
		return this;
	}

	public static final class Builder {
		private String submissionId;
		private String targetName;
		private Instant timestamp;
		private List<IndexerActionItem> actions;

		private Builder() {
		}

		public Builder withSubmissionId(String value) {
			submissionId = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withTimestamp(Instant value) {
			timestamp = value;
			return this;
		}

		public Builder withActions(List<IndexerActionItem> value) {
			actions = value == null ? null : List.copyOf(value);
			return this;
		}

		public TargetActionSubmitRequest build() {
			Objects.requireNonNull(targetName, "targetName");
			if (targetName.isBlank()) {
				throw new IllegalArgumentException("targetName must not be blank");
			}
			Objects.requireNonNull(actions, "actions");
			if (actions.isEmpty()) {
				throw new IllegalArgumentException("actions must not be empty");
			}
			return new TargetActionSubmitRequest()
				.setSubmissionId(submissionId)
				.setTargetName(targetName)
				.setTimestamp(timestamp)
				.setActions(actions);
		}
	}
}
