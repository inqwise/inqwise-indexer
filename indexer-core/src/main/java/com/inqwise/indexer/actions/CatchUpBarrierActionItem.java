package com.inqwise.indexer.actions;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import io.vertx.core.json.JsonObject;

public class CatchUpBarrierActionItem implements IndexerActionItem {
	public static final String TYPE = "type";
	public static final String TARGET_ID = "target_id";
	public static final String INDEXER_ID = "indexer_id";
	public static final String BARRIER_ID = "barrier_id";
	public static final String BARRIER_TIMESTAMP = "barrier_timestamp";

	private final Integer targetId;
	private final Integer indexerId;
	private final String barrierId;
	private final Instant barrierTimestamp;

	CatchUpBarrierActionItem(JsonObject json) {
		ActionItemValidation.requireOnlyFields(
			json,
			TYPE,
			TARGET_ID,
			INDEXER_ID,
			BARRIER_ID,
			BARRIER_TIMESTAMP
		);
		this.targetId = ActionItemValidation.optionalPositive(json.getInteger(TARGET_ID), "targetId");
		this.indexerId = ActionItemValidation.optionalPositive(
			json.getInteger(INDEXER_ID),
			"indexerId"
		);
		this.barrierId = ActionItemValidation.optionalText(json.getString(BARRIER_ID), "barrierId");
		this.barrierTimestamp = parseInstant(json.getString(BARRIER_TIMESTAMP));
	}

	private CatchUpBarrierActionItem(
		Integer targetId,
		Integer indexerId,
		String barrierId,
		Instant barrierTimestamp
	) {
		this.targetId = ActionItemValidation.optionalPositive(targetId, "targetId");
		this.indexerId = ActionItemValidation.optionalPositive(indexerId, "indexerId");
		this.barrierId = ActionItemValidation.optionalText(barrierId, "barrierId");
		this.barrierTimestamp = barrierTimestamp;
	}

	@Override
	public IndexerActionType getActionType() {
		return IndexerActionType.CATCH_UP_BARRIER;
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

		if (barrierId != null) {
			json.put(BARRIER_ID, barrierId);
		}

		if (barrierTimestamp != null) {
			json.put(BARRIER_TIMESTAMP, barrierTimestamp.toString());
		}

		return json;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public String getBarrierId() {
		return barrierId;
	}

	public Instant getBarrierTimestamp() {
		return barrierTimestamp;
	}

	public static Builder builder() {
		return new Builder();
	}

	private static Instant parseInstant(String value) {
		if (value == null) {
			return null;
		}

		try {
			return Instant.parse(value);
		} catch (DateTimeParseException error) {
			throw new IllegalArgumentException("barrierTimestamp must be an ISO-8601 instant", error);
		}
	}

	public static final class Builder {
		private Integer targetId;
		private Integer indexerId;
		private String barrierId;
		private Instant barrierTimestamp;

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

		public Builder withBarrierId(String barrierId) {
			this.barrierId = barrierId;
			return this;
		}

		public Builder withBarrierTimestamp(Instant barrierTimestamp) {
			this.barrierTimestamp = barrierTimestamp;
			return this;
		}

		public CatchUpBarrierActionItem build() {
			return new CatchUpBarrierActionItem(targetId, indexerId, barrierId, barrierTimestamp);
		}
	}
}
