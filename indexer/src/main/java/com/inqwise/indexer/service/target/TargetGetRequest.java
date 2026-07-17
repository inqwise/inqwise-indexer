package com.inqwise.indexer.service.target;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetGetRequest {
	private Integer id;
	private String uid;

	public TargetGetRequest() {
	}

	public TargetGetRequest(JsonObject json) {
		id = json.getInteger("id");
		uid = json.getString("uid");
	}

	public JsonObject toJson() {
		return new JsonObject().put("id", id).put("uid", uid);
	}

	public Integer getId() {
		return id;
	}

	public TargetGetRequest setId(Integer value) {
		id = value;
		return this;
	}

	public String getUid() {
		return uid;
	}

	public TargetGetRequest setUid(String value) {
		uid = value;
		return this;
	}
}
