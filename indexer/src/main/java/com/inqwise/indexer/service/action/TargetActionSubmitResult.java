package com.inqwise.indexer.service.action;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetActionSubmitResult {
	public static final class Keys {
		public static final String SUBMISSION_ID = "submission_id";
		public static final String STATE = "state";

		private Keys() {
		}
	}

	private String submissionId;
	private TargetActionSubmitState state;

	public TargetActionSubmitResult() {
	}

	public TargetActionSubmitResult(JsonObject json) {
		this.submissionId = json.getString(Keys.SUBMISSION_ID);
		this.state = TargetActionSubmitState.valueOf(
			json.getString(Keys.STATE, TargetActionSubmitState.ACCEPTED.name())
		);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();

		if (submissionId != null) {
			json.put(Keys.SUBMISSION_ID, submissionId);
		}

		if (state != null) {
			json.put(Keys.STATE, state.name());
		}

		return json;
	}

	public String getSubmissionId() {
		return submissionId;
	}

	public TargetActionSubmitResult setSubmissionId(String submissionId) {
		this.submissionId = submissionId;
		return this;
	}

	public TargetActionSubmitState getState() {
		return state;
	}

	public TargetActionSubmitResult setState(TargetActionSubmitState state) {
		this.state = state;
		return this;
	}
}
