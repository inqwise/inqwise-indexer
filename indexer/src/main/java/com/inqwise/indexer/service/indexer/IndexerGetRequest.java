package com.inqwise.indexer.service.indexer;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerGetRequest {
	private Integer id;
	private String uid;

	public IndexerGetRequest() {
	}

	public IndexerGetRequest(JsonObject json) {
		id = json.getInteger("id");
		uid = json.getString("uid");
	}

	public JsonObject toJson() {
		return new JsonObject().put("id", id).put("uid", uid);
	}

	public Integer getId() {
		return id;
	}

	public IndexerGetRequest setId(Integer value) {
		id = value;
		return this;
	}

	public String getUid() {
		return uid;
	}

	public IndexerGetRequest setUid(String value) {
		uid = value;
		return this;
	}
}
