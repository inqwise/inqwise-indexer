package com.inqwise.indexer.service.admin;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminResetIndexerQueueRequest {
	public static final class Keys {
		public static final String INDEXER_ID = "indexer_id";
		public static final String EXPECTED_VERSION = "expected_version";

		private Keys() {
		}
	}

	private Integer indexerId;
	private long expectedVersion;

	public AdminResetIndexerQueueRequest() {
	}

	public AdminResetIndexerQueueRequest(JsonObject json) {
		this.indexerId = json.getInteger(Keys.INDEXER_ID);
		this.expectedVersion = json.getLong(Keys.EXPECTED_VERSION, 0L);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.INDEXER_ID, indexerId)
			.put(Keys.EXPECTED_VERSION, expectedVersion);
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public AdminResetIndexerQueueRequest setIndexerId(Integer indexerId) {
		this.indexerId = indexerId;
		return this;
	}

	public long getExpectedVersion() {
		return expectedVersion;
	}

	public AdminResetIndexerQueueRequest setExpectedVersion(long expectedVersion) {
		this.expectedVersion = expectedVersion;
		return this;
	}
}
