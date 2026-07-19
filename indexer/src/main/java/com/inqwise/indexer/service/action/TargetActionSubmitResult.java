package com.inqwise.indexer.service.action;

import java.util.Objects;

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

	public static Builder builder() {
		return new Builder();
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

	public static final class Builder {
		private String submissionId;
		private TargetActionSubmitState state;

		private Builder() {
		}

		public Builder withSubmissionId(String value) {
			submissionId = value;
			return this;
		}

		public Builder withState(TargetActionSubmitState value) {
			state = value;
			return this;
		}

		public TargetActionSubmitResult build() {
			Objects.requireNonNull(submissionId, "submissionId");
			if (submissionId.isBlank()) {
				throw new IllegalArgumentException("submissionId must not be blank");
			}
			return new TargetActionSubmitResult()
				.setSubmissionId(submissionId)
				.setState(Objects.requireNonNull(state, "state"));
		}
	}
}
