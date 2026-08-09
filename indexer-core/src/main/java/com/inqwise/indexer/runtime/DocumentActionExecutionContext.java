package com.inqwise.indexer.runtime;

import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionType;

import io.vertx.core.json.JsonObject;

public record DocumentActionExecutionContext(
	int targetId,
	int indexerId,
	String indexName,
	String documentUid,
	IndexerActionType actionType,
	JsonObject document
) {
	public DocumentActionExecutionContext {
		Objects.requireNonNull(indexName, "indexName");
		Objects.requireNonNull(documentUid, "documentUid");
		Objects.requireNonNull(actionType, "actionType");
		if (actionType != IndexerActionType.PUT_DOCUMENT
			&& actionType != IndexerActionType.REMOVE_DOCUMENT) {
			throw new IllegalArgumentException(
				"Document action context requires a document mutation action"
			);
		}
		if (actionType == IndexerActionType.PUT_DOCUMENT && document == null) {
			throw new IllegalArgumentException("PUT document context requires document");
		}
		if (actionType == IndexerActionType.REMOVE_DOCUMENT && document != null) {
			throw new IllegalArgumentException("REMOVE document context must not include document");
		}
		document = document == null ? null : document.copy();
	}

	@Override
	public JsonObject document() {
		return document == null ? null : document.copy();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private Integer indexerId;
		private String indexName;
		private String documentUid;
		private IndexerActionType actionType;
		private JsonObject document;

		private Builder() {
		}

		public Builder withTargetId(int value) {
			targetId = value;
			return this;
		}

		public Builder withIndexerId(int value) {
			indexerId = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withDocumentUid(String value) {
			documentUid = value;
			return this;
		}

		public Builder withActionType(IndexerActionType value) {
			actionType = value;
			return this;
		}

		public Builder withDocument(JsonObject value) {
			document = value == null ? null : value.copy();
			return this;
		}

		public DocumentActionExecutionContext build() {
			return new DocumentActionExecutionContext(
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(indexName, "indexName"),
				Objects.requireNonNull(documentUid, "documentUid"),
				Objects.requireNonNull(actionType, "actionType"),
				document
			);
		}
	}
}
