package com.inqwise.indexer.service.action;

import java.time.Instant;
import java.util.List;

import com.inqwise.indexer.IndexerActionItem;

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

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.SUBMISSION_ID, submissionId)
			.put(Keys.TARGET_NAME, targetName)
			.put(Keys.TIMESTAMP, timestamp == null ? null : timestamp.toString())
			.put(Keys.ACTIONS, new JsonArray(actions.stream()
				.map(IndexerActionItem::toJson)
				.toList()));
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
}
