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

	public static Builder builder() {
		return new Builder();
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

		public TargetGetRequest build() {
			boolean hasId = id != null;
			boolean hasUid = uid != null && !uid.isBlank();
			if (hasId == hasUid) {
				throw new IllegalArgumentException("Exactly one target lookup key is required");
			}
			return new TargetGetRequest().setId(id).setUid(hasUid ? uid : null);
		}
	}
}
