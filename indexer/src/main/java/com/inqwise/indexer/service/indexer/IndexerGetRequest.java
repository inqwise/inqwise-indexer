package com.inqwise.indexer.service.indexer;

import java.util.Objects;

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

		public IndexerGetRequest build() {
			boolean hasId = id != null;
			boolean hasUid = uid != null && !uid.isBlank();
			if (hasId == hasUid) {
				throw new IllegalArgumentException("Exactly one indexer lookup key is required");
			}
			return new IndexerGetRequest()
				.setId(id)
				.setUid(hasUid ? Objects.requireNonNull(uid, "uid") : null);
		}
	}
}
