package com.inqwise.indexer.load.service;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class LoadCancelRequest {
	private Integer indexerId;
	private String reason;
	private Long expectedVersion;

	public LoadCancelRequest() {
	}

	public LoadCancelRequest(JsonObject json) {
		indexerId = json.getInteger("indexer_id");
		reason = json.getString("reason");
		expectedVersion = json.getLong("expected_version");
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("reason", reason)
			.put("expected_version", expectedVersion);
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public LoadCancelRequest setIndexerId(Integer value) {
		indexerId = value;
		return this;
	}

	public String getReason() {
		return reason;
	}

	public LoadCancelRequest setReason(String value) {
		reason = value;
		return this;
	}

	public Long getExpectedVersion() {
		return expectedVersion;
	}

	public LoadCancelRequest setExpectedVersion(Long value) {
		expectedVersion = value;
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private String reason;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withReason(String value) {
			reason = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public LoadCancelRequest build() {
			return new LoadCancelRequest()
				.setIndexerId(Objects.requireNonNull(indexerId, "indexerId"))
				.setReason(reason)
				.setExpectedVersion(Objects.requireNonNull(expectedVersion, "expectedVersion"));
		}
	}
}
