package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class RemoveDocumentActionItem implements IndexerActionItem {
	public static final String TYPE = "type";
	public static final String TARGET_ID = "target_id";
	public static final String INDEXER_ID = "indexer_id";
	public static final String INDEX_NAME = "index_name";
	public static final String UID = "uid";

	private final Integer targetId;
	private final Integer indexerId;
	private final String indexName;
	private final String uid;

	public RemoveDocumentActionItem(JsonObject json) {
		this(
			json.getInteger(TARGET_ID),
			json.getInteger(INDEXER_ID),
			json.getString(INDEX_NAME),
			json.getString(UID)
		);
	}

	RemoveDocumentActionItem(
		Integer targetId,
		Integer indexerId,
		String indexName,
		String uid
	) {
		this.targetId = targetId;
		this.indexerId = indexerId;
		this.indexName = indexName;
		this.uid = Objects.requireNonNull(uid, "uid");
	}

	@Override
	public IndexerActionType getActionType() {
		return IndexerActionType.REMOVE_DOCUMENT;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put(TYPE, getActionType().name())
			.put(UID, uid);

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

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String uid;

		private Builder() {
		}

		public Builder withUid(String uid) {
			this.uid = uid;
			return this;
		}

		public RemoveDocumentActionItem build() {
			return new RemoveDocumentActionItem(
				null,
				null,
				null,
				uid
			);
		}
	}
}
