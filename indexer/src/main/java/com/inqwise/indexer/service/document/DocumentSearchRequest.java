package com.inqwise.indexer.service.document;

import java.time.Instant;

import com.inqwise.indexer.documents.DocumentQuery;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class DocumentSearchRequest {
	private String targetName;
	private String queryText = "";
	private Long fromEpochMs;
	private Long toEpochMs;
	private int offset;
	private int limit = DocumentQuery.DEFAULT_LIMIT;

	public DocumentSearchRequest() {
	}

	public DocumentSearchRequest(JsonObject json) {
		targetName = json.getString("target_name");
		queryText = json.getString("query_text", "");
		fromEpochMs = json.getLong("from_epoch_ms");
		toEpochMs = json.getLong("to_epoch_ms");
		offset = json.getInteger("offset", 0);
		limit = json.getInteger("limit", DocumentQuery.DEFAULT_LIMIT);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("target_name", targetName)
			.put("query_text", queryText)
			.put("from_epoch_ms", fromEpochMs)
			.put("to_epoch_ms", toEpochMs)
			.put("offset", offset)
			.put("limit", limit);
	}

	DocumentQuery toDomainQuery() {
		return DocumentQuery.builder()
			.withTargetName(targetName)
			.withQueryText(queryText)
			.withFromInclusive(fromEpochMs == null ? Instant.MIN : Instant.ofEpochMilli(fromEpochMs))
			.withToExclusive(toEpochMs == null ? Instant.MAX : Instant.ofEpochMilli(toEpochMs))
			.withOffset(offset)
			.withLimit(limit)
			.build();
	}

	public String getTargetName() {
		return targetName;
	}

	public DocumentSearchRequest setTargetName(String value) {
		targetName = value;
		return this;
	}

	public String getQueryText() {
		return queryText;
	}

	public DocumentSearchRequest setQueryText(String value) {
		queryText = value == null ? "" : value;
		return this;
	}

	public Long getFromEpochMs() {
		return fromEpochMs;
	}

	public DocumentSearchRequest setFromEpochMs(Long value) {
		fromEpochMs = value;
		return this;
	}

	public Long getToEpochMs() {
		return toEpochMs;
	}

	public DocumentSearchRequest setToEpochMs(Long value) {
		toEpochMs = value;
		return this;
	}

	public int getOffset() {
		return offset;
	}

	public DocumentSearchRequest setOffset(int value) {
		offset = value;
		return this;
	}

	public int getLimit() {
		return limit;
	}

	public DocumentSearchRequest setLimit(int value) {
		limit = value;
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String targetName;
		private String queryText = "";
		private Long fromEpochMs;
		private Long toEpochMs;
		private int offset;
		private int limit = DocumentQuery.DEFAULT_LIMIT;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withQueryText(String value) {
			queryText = value == null ? "" : value;
			return this;
		}

		public Builder withFromEpochMs(Long value) {
			fromEpochMs = value;
			return this;
		}

		public Builder withToEpochMs(Long value) {
			toEpochMs = value;
			return this;
		}

		public Builder withOffset(int value) {
			offset = value;
			return this;
		}

		public Builder withLimit(int value) {
			limit = value;
			return this;
		}

		public DocumentSearchRequest build() {
			return new DocumentSearchRequest()
				.setTargetName(targetName)
				.setQueryText(queryText)
				.setFromEpochMs(fromEpochMs)
				.setToEpochMs(toEpochMs)
				.setOffset(offset)
				.setLimit(limit);
		}
	}
}
