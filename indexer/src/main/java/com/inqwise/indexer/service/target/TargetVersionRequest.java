package com.inqwise.indexer.service.target;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetVersionRequest {
	private Integer targetId;
	private Long expectedVersion;

	public TargetVersionRequest() {
	}

	public TargetVersionRequest(JsonObject json) {
		targetId = json.getInteger("target_id");
		expectedVersion = json.getLong("expected_version");
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("target_id", targetId)
			.put("expected_version", expectedVersion);
	}

	public Integer getTargetId() {
		return targetId;
	}

	public TargetVersionRequest setTargetId(Integer value) {
		targetId = value;
		return this;
	}

	public Long getExpectedVersion() {
		return expectedVersion;
	}

	public TargetVersionRequest setExpectedVersion(Long value) {
		expectedVersion = value;
		return this;
	}
}
