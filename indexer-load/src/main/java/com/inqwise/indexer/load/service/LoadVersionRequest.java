package com.inqwise.indexer.load.service;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class LoadVersionRequest {
	private Integer indexerId;
	private Long expectedVersion;

	public LoadVersionRequest() {
	}

	public LoadVersionRequest(JsonObject json) {
		indexerId = json.getInteger("indexer_id");
		expectedVersion = json.getLong("expected_version");
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("expected_version", expectedVersion);
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public LoadVersionRequest setIndexerId(Integer value) {
		indexerId = value;
		return this;
	}

	public Long getExpectedVersion() {
		return expectedVersion;
	}

	public LoadVersionRequest setExpectedVersion(Long value) {
		expectedVersion = value;
		return this;
	}
}
