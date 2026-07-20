package com.inqwise.indexer.service.admin;

import java.util.Objects;

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

	public static Builder builder() {
		return new Builder();
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

	public static final class Builder {
		private Integer indexerId;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withExpectedVersion(Long value) {
			expectedVersion = value;
			return this;
		}

		public AdminDeleteIndexerRequest build() {
			return new AdminDeleteIndexerRequest()
				.setIndexerId(Objects.requireNonNull(indexerId, "indexerId"))
				.setExpectedVersion(Objects.requireNonNull(expectedVersion, "expectedVersion"));
		}
	}
}
