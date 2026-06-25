package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
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

	public PutDocumentActionItem(JsonObject json) {
		this(
			json.getInteger(TARGET_ID),
			json.getInteger(INDEXER_ID),
			json.getString(INDEX_NAME),
			json.getString(UID),
			json.getJsonObject(DOCUMENT, new JsonObject())
		);
	}

	private PutDocumentActionItem(
		Integer targetId,
		Integer indexerId,
		String indexName,
		String uid,
		JsonObject document
	) {
		this.targetId = targetId;
		this.indexerId = indexerId;
		this.indexName = indexName;
		this.uid = Objects.requireNonNull(uid, "uid");
		this.document = document == null ? new JsonObject() : document.copy();
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

		public Builder withTargetId(Integer targetId) {
			this.targetId = targetId;
			return this;
		}

		public Builder withIndexerId(Integer indexerId) {
			this.indexerId = indexerId;
			return this;
		}

		public Builder withIndexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		public Builder withUid(String uid) {
			this.uid = uid;
			return this;
		}

		public Builder withDocument(JsonObject document) {
			this.document = document;
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
