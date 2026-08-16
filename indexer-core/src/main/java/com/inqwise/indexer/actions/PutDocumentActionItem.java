package com.inqwise.indexer.actions;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class PutDocumentActionItem implements IndexerActionItem {
	public static final String TYPE = "type";
	public static final String TARGET_ID = "target_id";
	public static final String INDEXER_ID = "indexer_id";
	public static final String INDEX_NAME = "index_name";
	public static final String UID = "uid";
	public static final String DOCUMENT = "document";

	private final Integer targetId;
	private final Integer indexerId;
	private final String indexName;
	private final String uid;
	private final JsonObject document;

	PutDocumentActionItem(JsonObject json) {
		ActionItemValidation.requireOnlyFields(
			json,
			TYPE,
			TARGET_ID,
			INDEXER_ID,
			INDEX_NAME,
			UID,
			DOCUMENT
		);
		this.targetId = ActionItemValidation.optionalPositive(
			json.getInteger(TARGET_ID),
			"targetId"
		);
		this.indexerId = ActionItemValidation.optionalPositive(
			json.getInteger(INDEXER_ID),
			"indexerId"
		);
		this.indexName = ActionItemValidation.optionalText(json.getString(INDEX_NAME), "indexName");
		this.uid = ActionItemValidation.requiredText(json.getString(UID), "uid");
		this.document = Objects.requireNonNull(json.getJsonObject(DOCUMENT), "document").copy();
	}

	PutDocumentActionItem(
		Integer targetId,
		Integer indexerId,
		String indexName,
		String uid,
		JsonObject document
	) {
		this.targetId = ActionItemValidation.optionalPositive(targetId, "targetId");
		this.indexerId = ActionItemValidation.optionalPositive(indexerId, "indexerId");
		this.indexName = ActionItemValidation.optionalText(indexName, "indexName");
		this.uid = ActionItemValidation.requiredText(uid, "uid");
		this.document = Objects.requireNonNull(document, "document").copy();
	}

	@Override
	public IndexerActionType getActionType() {
		return IndexerActionType.PUT_DOCUMENT;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put(TYPE, getActionType().name())
			.put(UID, uid)
			.put(DOCUMENT, document.copy());

		if (targetId != null) {
			json.put(TARGET_ID, targetId);
		}

		if (indexerId != null) {
			json.put(INDEXER_ID, indexerId);
		}

		if (indexName != null) {
			json.put(INDEX_NAME, indexName);
		}

		return json;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public String getIndexName() {
		return indexName;
	}

	public String getUid() {
		return uid;
	}

	public JsonObject getDocument() {
		return document.copy();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private Integer indexerId;
		private String indexName;
		private String uid;
		private JsonObject document;

		private Builder() {
		}

		Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withUid(String uid) {
			this.uid = uid;
			return this;
		}

		public Builder withDocument(JsonObject document) {
			this.document = document == null ? null : document.copy();
			return this;
		}

		public PutDocumentActionItem build() {
			return new PutDocumentActionItem(
				targetId,
				indexerId,
				indexName,
				uid,
				document
			);
		}
	}
}
