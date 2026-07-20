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

	public static Builder builder() {
		return new Builder();
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

	public static final class Builder {
		private Integer id;
		private String uid;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withUid(String value) {
			uid = value;
			return this;
		}

		public AdminTargetGetRequest build() {
			validateSelector(id, uid);
			return new AdminTargetGetRequest()
				.setId(id)
				.setUid(uid);
		}
	}

	private static void validateSelector(Integer id, String uid) {
		if (id == null && (uid == null || uid.isBlank())) {
			throw new IllegalArgumentException("id or uid is required");
		}
		if (id != null && uid != null) {
			throw new IllegalArgumentException("id and uid are mutually exclusive");
		}
	}
}
