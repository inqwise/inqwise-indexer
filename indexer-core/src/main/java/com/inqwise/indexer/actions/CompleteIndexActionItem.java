package com.inqwise.indexer.actions;

import io.vertx.core.json.JsonObject;

public class CompleteIndexActionItem implements IndexerActionItem {
	public static final String TYPE = "type";
	public static final String TARGET_ID = "target_id";
	public static final String INDEXER_ID = "indexer_id";

	private final Integer targetId;
	private final Integer indexerId;

	CompleteIndexActionItem(JsonObject json) {
		ActionItemValidation.requireOnlyFields(
			json,
			TYPE,
			TARGET_ID,
			INDEXER_ID
		);
		this.targetId = ActionItemValidation.optionalPositive(json.getInteger(TARGET_ID), "targetId");
		this.indexerId = ActionItemValidation.optionalPositive(
			json.getInteger(INDEXER_ID),
			"indexerId"
		);
	}

	private CompleteIndexActionItem(Integer targetId, Integer indexerId) {
		this.targetId = ActionItemValidation.optionalPositive(targetId, "targetId");
		this.indexerId = ActionItemValidation.optionalPositive(indexerId, "indexerId");
	}

	@Override
	public IndexerActionType getActionType() {
		return IndexerActionType.COMPLETE;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put(TYPE, getActionType().name());

		if (targetId != null) {
			json.put(TARGET_ID, targetId);
		}

		if (indexerId != null) {
			json.put(INDEXER_ID, indexerId);
		}

		return json;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private Integer indexerId;

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

		public CompleteIndexActionItem build() {
			return new CompleteIndexActionItem(targetId, indexerId);
		}
	}
}
