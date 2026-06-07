package com.inqwise.indexer.service.admin;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetGetRequest {
	public static final class Keys {
		public static final String ID = "id";
		public static final String UID = "uid";

		private Keys() {
		}
	}

	private Integer id;
	private String uid;

	public AdminTargetGetRequest() {
	}

	public AdminTargetGetRequest(JsonObject json) {
		this.id = json.getInteger(Keys.ID);
		this.uid = json.getString(Keys.UID);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.ID, id)
			.put(Keys.UID, uid);
	}

	public Integer getId() {
		return id;
	}

	public AdminTargetGetRequest setId(Integer id) {
		this.id = id;
		return this;
	}

	public String getUid() {
		return uid;
	}

	public AdminTargetGetRequest setUid(String uid) {
		this.uid = uid;
		return this;
	}
}
