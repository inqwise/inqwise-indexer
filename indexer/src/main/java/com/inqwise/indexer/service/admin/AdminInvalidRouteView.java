package com.inqwise.indexer.service.admin;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.routing.InvalidRouteRecord;
import com.inqwise.indexer.routing.InvalidRouteSignature;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminInvalidRouteView {
	public static final class Keys {
		public static final String SIGNATURE = "signature";
		public static final String TARGET_NAME = "target_name";
		public static final String PERIOD_KEY = "period_key";
		public static final String TARGET_ID = "target_id";
		public static final String INDEXER_ID = "indexer_id";
		public static final String INDEX_NAME = "index_name";
		public static final String ACTION_TYPE = "action_type";
		public static final String REASON = "reason";
		public static final String FIRST_SEEN_AT = "first_seen_at";
		public static final String LAST_SEEN_AT = "last_seen_at";
		public static final String EXPIRES_AT = "expires_at";
		public static final String COUNT = "count";

		private Keys() {
		}
	}

	private String targetName;
	private String periodKey;
	private Integer targetId;
	private Integer indexerId;
	private String indexName;
	private IndexerActionType actionType;
	private String reason;
	private Instant firstSeenAt;
	private Instant lastSeenAt;
	private Instant expiresAt;
	private long count;

	public AdminInvalidRouteView() {
	}

	public AdminInvalidRouteView(JsonObject json) {
		JsonObject signature = json.getJsonObject(Keys.SIGNATURE, new JsonObject());
		this.targetName = signature.getString(Keys.TARGET_NAME);
		this.periodKey = signature.getString(Keys.PERIOD_KEY);
		this.targetId = signature.getInteger(Keys.TARGET_ID);
		this.indexerId = signature.getInteger(Keys.INDEXER_ID);
		this.indexName = signature.getString(Keys.INDEX_NAME);
		String action = signature.getString(Keys.ACTION_TYPE);
		this.actionType = action == null ? null : IndexerActionType.valueOf(action);
		this.reason = json.getString(Keys.REASON);
		this.firstSeenAt = instant(json, Keys.FIRST_SEEN_AT);
		this.lastSeenAt = instant(json, Keys.LAST_SEEN_AT);
		this.expiresAt = instant(json, Keys.EXPIRES_AT);
		this.count = json.getLong(Keys.COUNT, 0L);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static AdminInvalidRouteView from(InvalidRouteRecord record) {
		Objects.requireNonNull(record, "record");
		InvalidRouteSignature signature = record.signature();
		return builder()
			.withTargetName(signature.targetName())
			.withPeriodKey(signature.periodKey())
			.withTargetId(signature.targetId())
			.withIndexerId(signature.indexerId())
			.withIndexName(signature.indexName())
			.withActionType(signature.actionType())
			.withReason(record.reason())
			.withFirstSeenAt(record.firstSeenAt())
			.withLastSeenAt(record.lastSeenAt())
			.withExpiresAt(record.expiresAt())
			.withCount(record.count())
			.build();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.SIGNATURE, new JsonObject()
				.put(Keys.TARGET_NAME, targetName)
				.put(Keys.PERIOD_KEY, periodKey)
				.put(Keys.TARGET_ID, targetId)
				.put(Keys.INDEXER_ID, indexerId)
				.put(Keys.INDEX_NAME, indexName)
				.put(Keys.ACTION_TYPE, actionType == null ? null : actionType.name()))
			.put(Keys.REASON, reason)
			.put(Keys.FIRST_SEEN_AT, string(firstSeenAt))
			.put(Keys.LAST_SEEN_AT, string(lastSeenAt))
			.put(Keys.EXPIRES_AT, string(expiresAt))
			.put(Keys.COUNT, count);
	}

	private static Instant instant(JsonObject json, String key) {
		String value = json.getString(key);
		return value == null ? null : Instant.parse(value);
	}

	private static String string(Instant value) {
		return value == null ? null : value.toString();
	}

	public static final class Builder {
		private String targetName;
		private String periodKey;
		private Integer targetId;
		private Integer indexerId;
		private String indexName;
		private IndexerActionType actionType;
		private String reason;
		private Instant firstSeenAt;
		private Instant lastSeenAt;
		private Instant expiresAt;
		private Long count;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withPeriodKey(String value) {
			periodKey = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withActionType(IndexerActionType value) {
			actionType = value;
			return this;
		}

		public Builder withReason(String value) {
			reason = value;
			return this;
		}

		public Builder withFirstSeenAt(Instant value) {
			firstSeenAt = value;
			return this;
		}

		public Builder withLastSeenAt(Instant value) {
			lastSeenAt = value;
			return this;
		}

		public Builder withExpiresAt(Instant value) {
			expiresAt = value;
			return this;
		}

		public Builder withCount(long value) {
			count = value;
			return this;
		}

		public AdminInvalidRouteView build() {
			AdminInvalidRouteView view = new AdminInvalidRouteView();
			view.targetName = targetName;
			view.periodKey = periodKey;
			view.targetId = targetId;
			view.indexerId = indexerId;
			view.indexName = indexName;
			view.actionType = Objects.requireNonNull(actionType, "actionType");
			view.reason = Objects.requireNonNull(reason, "reason");
			view.firstSeenAt = Objects.requireNonNull(firstSeenAt, "firstSeenAt");
			view.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
			view.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
			view.count = Objects.requireNonNull(count, "count");
			return view;
		}
	}
}
