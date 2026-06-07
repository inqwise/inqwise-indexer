package com.inqwise.indexer.service.admin;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminDeleteIndexerRequest {
	public static final class Keys {
		public static final String INDEXER_ID = "indexer_id";
		public static final String EXPECTED_VERSION = "expected_version";

		private Keys() {
		}
	}

	private Integer indexerId;
	private Long expectedVersion;

	public AdminDeleteIndexerRequest() {
	}

	public AdminDeleteIndexerRequest(JsonObject json) {
		this.indexerId = json.getInteger(Keys.INDEXER_ID);
		this.expectedVersion = json.getLong(Keys.EXPECTED_VERSION);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject().put(Keys.INDEXER_ID, indexerId);
		if (expectedVersion != null) {
			json.put(Keys.EXPECTED_VERSION, expectedVersion);
		}

		return json;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public AdminDeleteIndexerRequest setIndexerId(Integer indexerId) {
		this.indexerId = indexerId;
		return this;
	}

	public Long getExpectedVersion() {
		return expectedVersion;
	}

	public AdminDeleteIndexerRequest setExpectedVersion(Long expectedVersion) {
		this.expectedVersion = expectedVersion;
		return this;
	}
}
