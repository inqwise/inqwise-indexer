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
	public static final String SEQUENCE = "sequence";
	public static final String MUTATION_ID = "mutation_id";
	public static final String DOCUMENT = "document";

	private final Integer targetId;
	private final Integer indexerId;
	private final String indexName;
	private final String uid;
	private final Long sequence;
	private final String mutationId;
	private final JsonObject document;

	public PutDocumentActionItem(JsonObject json) {
		this(
			json.getInteger(TARGET_ID),
			json.getInteger(INDEXER_ID),
			json.getString(INDEX_NAME),
			json.getString(UID),
			json.getLong(SEQUENCE),
			json.getString(MUTATION_ID),
			json.getJsonObject(DOCUMENT, new JsonObject())
		);
	}

	private PutDocumentActionItem(
		Integer targetId,
		Integer indexerId,
		String indexName,
		String uid,
		Long sequence,
		String mutationId,
		JsonObject document
	) {
		this.targetId = targetId;
		this.indexerId = indexerId;
		this.indexName = indexName;
		this.uid = Objects.requireNonNull(uid, "uid");
		this.sequence = sequence;
		this.mutationId = mutationId;
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

		if (targetId != null) {
			json.put(TARGET_ID, targetId);
		}

		if (indexerId != null) {
			json.put(INDEXER_ID, indexerId);
		}

		if (sequence != null) {
			json.put(SEQUENCE, sequence);
		}

		if (mutationId != null) {
			json.put(MUTATION_ID, mutationId);
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

	public Long getSequence() {
		return sequence;
	}

	public String getMutationId() {
		return mutationId;
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
		private Long sequence;
		private String mutationId;
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

		public Builder withSequence(Long sequence) {
			this.sequence = sequence;
			return this;
		}

		public Builder withMutationId(String mutationId) {
			this.mutationId = mutationId;
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
				sequence,
				mutationId,
				document
			);
		}
	}
}
