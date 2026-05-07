package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class PutDocumentActionItem implements IndexerActionItem {
	public static final String TYPE = "type";
	public static final String TARGET_NAME = "target_name";
	public static final String UID = "uid";
	public static final String DOCUMENT = "document";

	private final String targetName;
	private final String uid;
	private final JsonObject document;

	public PutDocumentActionItem(JsonObject json) {
		this(
			json.getString(TARGET_NAME),
			json.getString(UID),
			json.getJsonObject(DOCUMENT, new JsonObject())
		);
	}

	private PutDocumentActionItem(String targetName, String uid, JsonObject document) {
		this.targetName = targetName;
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

		if (targetName != null) {
			json.put(TARGET_NAME, targetName);
		}

		return json;
	}

	public String getTargetName() {
		return targetName;
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
		private String targetName;
		private String uid;
		private JsonObject document;

		private Builder() {
		}

		public Builder withTargetName(String targetName) {
			this.targetName = targetName;
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
			return new PutDocumentActionItem(targetName, uid, document);
		}
	}
}
