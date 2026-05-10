package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class PutDocumentActionItem implements IndexerActionItem {
	public static final String TYPE = "type";
	public static final String INDEX_NAME = "index_name";
	public static final String UID = "uid";
	public static final String DOCUMENT = "document";

	private final String indexName;
	private final String uid;
	private final JsonObject document;

	public PutDocumentActionItem(JsonObject json) {
		this(
			json.getString(INDEX_NAME),
			json.getString(UID),
			json.getJsonObject(DOCUMENT, new JsonObject())
		);
	}

	private PutDocumentActionItem(String indexName, String uid, JsonObject document) {
		this.indexName = Objects.requireNonNull(indexName, "indexName");
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
			.put(INDEX_NAME, indexName)
			.put(UID, uid)
			.put(DOCUMENT, document.copy());

		return json;
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
		private String indexName;
		private String uid;
		private JsonObject document;

		private Builder() {
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
			return new PutDocumentActionItem(indexName, uid, document);
		}
	}
}
