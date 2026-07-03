package com.inqwise.indexer.service.admin;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminIndexerLifecycleRequest {
	public static final class Keys {
		public static final String INDEXER_ID = "indexer_id";
		public static final String EXPECTED_VERSION = "expected_version";

		private Keys() {
		}
	}

	private Integer indexerId;
	private Long expectedVersion;

	public AdminIndexerLifecycleRequest() {
	}

	public AdminIndexerLifecycleRequest(JsonObject json) {
		this.indexerId = json.getInteger(Keys.INDEXER_ID);
		this.expectedVersion = json.getLong(Keys.EXPECTED_VERSION);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.INDEXER_ID, indexerId)
			.put(Keys.EXPECTED_VERSION, expectedVersion);
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public AdminIndexerLifecycleRequest setIndexerId(Integer indexerId) {
		this.indexerId = indexerId;
		return this;
	}

	public Long getExpectedVersion() {
		return expectedVersion;
	}

	public AdminIndexerLifecycleRequest setExpectedVersion(Long expectedVersion) {
		this.expectedVersion = expectedVersion;
		return this;
	}
}
