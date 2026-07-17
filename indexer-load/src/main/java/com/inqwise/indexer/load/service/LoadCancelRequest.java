package com.inqwise.indexer.load.service;

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
}
