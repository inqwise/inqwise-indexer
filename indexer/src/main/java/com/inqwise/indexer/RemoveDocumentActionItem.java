package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class RemoveDocumentActionItem implements IndexerActionItem {
	public static final String TYPE = "type";
	public static final String TARGET_ID = "target_id";
	public static final String INDEXER_ID = "indexer_id";
	public static final String TARGET_NAME = "target_name";
	public static final String INDEX_NAME = "index_name";
	public static final String UID = "uid";
	public static final String SEQUENCE = "sequence";
	public static final String MUTATION_ID = "mutation_id";

	private final Integer targetId;
	private final Integer indexerId;
	private final String targetName;
	private final String indexName;
	private final String uid;
	private final Long sequence;
	private final String mutationId;

	public RemoveDocumentActionItem(JsonObject json) {
		this(
			json.getInteger(TARGET_ID),
			json.getInteger(INDEXER_ID),
			json.getString(TARGET_NAME),
			json.getString(INDEX_NAME),
			json.getString(UID),
			json.getLong(SEQUENCE),
			json.getString(MUTATION_ID)
		);
	}

	private RemoveDocumentActionItem(
		Integer targetId,
		Integer indexerId,
		String targetName,
		String indexName,
		String uid,
		Long sequence,
		String mutationId
	) {
		this.targetId = targetId;
		this.indexerId = indexerId;
		this.targetName = targetName;
		this.indexName = indexName;
		this.uid = Objects.requireNonNull(uid, "uid");
		this.sequence = sequence;
		this.mutationId = mutationId;
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

		if (targetName != null) {
			json.put(TARGET_NAME, targetName);
		}

		if (indexName != null) {
			json.put(INDEX_NAME, indexName);
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

	public String getTargetName() {
		return targetName;
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

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private Integer indexerId;
		private String targetName;
		private String indexName;
		private String uid;
		private Long sequence;
		private String mutationId;

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

		public Builder withTargetName(String targetName) {
			this.targetName = targetName;
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

		public RemoveDocumentActionItem build() {
			return new RemoveDocumentActionItem(
				targetId,
				indexerId,
				targetName,
				indexName,
				uid,
				sequence,
				mutationId
			);
		}
	}
}
