package com.inqwise.indexer.service.indexer;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerVersionRequest {
	private Integer indexerId;
	private Long expectedVersion;

	public IndexerVersionRequest() {
	}

	public IndexerVersionRequest(JsonObject json) {
		indexerId = json.getInteger("indexer_id");
		expectedVersion = json.getLong("expected_version");
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("expected_version", expectedVersion);
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public IndexerVersionRequest setIndexerId(Integer value) {
		indexerId = value;
		return this;
	}

	public Long getExpectedVersion() {
		return expectedVersion;
	}

	public IndexerVersionRequest setExpectedVersion(Long value) {
		expectedVersion = value;
		return this;
	}

	public static Builder builder() {
		return new Builder();
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

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public IndexerVersionRequest build() {
			return new IndexerVersionRequest()
				.setIndexerId(Objects.requireNonNull(indexerId, "indexerId"))
				.setExpectedVersion(Objects.requireNonNull(expectedVersion, "expectedVersion"));
		}
	}
}
