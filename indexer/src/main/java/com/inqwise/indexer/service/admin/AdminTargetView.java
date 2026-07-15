package com.inqwise.indexer.service.admin;

import java.time.Instant;

import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.catalog.targets.TargetStatus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetView {
	public static final class Keys {
		public static final String ID = "id";
		public static final String UID = "uid";
		public static final String TARGET_NAME = "target_name";
		public static final String PERIOD_KEY = "period_key";
		public static final String PERIOD_START_INCLUSIVE = "period_start_inclusive";
		public static final String PERIOD_END_EXCLUSIVE = "period_end_exclusive";
		public static final String STATUS = "status";
		public static final String PROVISIONING_STATE = "provisioning_state";
		public static final String CREATED_AT = "created_at";
		public static final String UPDATED_AT = "updated_at";
		public static final String VERSION = "version";

		private Keys() {
		}
	}

	private Integer id;
	private String uid;
	private String targetName;
	private String periodKey;
	private Instant periodStartInclusive;
	private Instant periodEndExclusive;
	private TargetStatus status;
	private TargetProvisioningState provisioningState;
	private Instant createdAt;
	private Instant updatedAt;
	private long version;

	public AdminTargetView() {
	}

	public AdminTargetView(JsonObject json) {
		this.id = json.getInteger(Keys.ID);
		this.uid = json.getString(Keys.UID);
		this.targetName = json.getString(Keys.TARGET_NAME);
		this.periodKey = json.getString(Keys.PERIOD_KEY);
		this.periodStartInclusive = instant(json, Keys.PERIOD_START_INCLUSIVE);
		this.periodEndExclusive = instant(json, Keys.PERIOD_END_EXCLUSIVE);
		this.status = enumValue(json, Keys.STATUS, TargetStatus.class);
		this.provisioningState = enumValue(json, Keys.PROVISIONING_STATE, TargetProvisioningState.class);
		this.createdAt = instant(json, Keys.CREATED_AT);
		this.updatedAt = instant(json, Keys.UPDATED_AT);
		this.version = json.getLong(Keys.VERSION, 0L);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.ID, id)
			.put(Keys.UID, uid)
			.put(Keys.TARGET_NAME, targetName)
			.put(Keys.PERIOD_KEY, periodKey)
			.put(Keys.PERIOD_START_INCLUSIVE, string(periodStartInclusive))
			.put(Keys.PERIOD_END_EXCLUSIVE, string(periodEndExclusive))
			.put(Keys.STATUS, name(status))
			.put(Keys.PROVISIONING_STATE, name(provisioningState))
			.put(Keys.CREATED_AT, string(createdAt))
			.put(Keys.UPDATED_AT, string(updatedAt))
			.put(Keys.VERSION, version);
	}

	public static AdminTargetView from(TargetRecord record) {
		return new AdminTargetView()
			.setId(record.id())
			.setUid(record.uid())
			.setTargetName(record.targetName())
			.setPeriodKey(record.periodKey())
			.setPeriodStartInclusive(record.periodStartInclusive())
			.setPeriodEndExclusive(record.periodEndExclusive())
			.setStatus(record.status())
			.setProvisioningState(record.provisioningState())
			.setCreatedAt(record.createdAt())
			.setUpdatedAt(record.updatedAt())
			.setVersion(record.version());
	}

	public Integer getId() {
		return id;
	}

	public AdminTargetView setId(Integer id) {
		this.id = id;
		return this;
	}

	public String getUid() {
		return uid;
	}

	public AdminTargetView setUid(String uid) {
		this.uid = uid;
		return this;
	}

	public String getTargetName() {
		return targetName;
	}

	public AdminTargetView setTargetName(String targetName) {
		this.targetName = targetName;
		return this;
	}

	public String getPeriodKey() {
		return periodKey;
	}

	public AdminTargetView setPeriodKey(String periodKey) {
		this.periodKey = periodKey;
		return this;
	}

	public Instant getPeriodStartInclusive() {
		return periodStartInclusive;
	}

	public AdminTargetView setPeriodStartInclusive(Instant periodStartInclusive) {
		this.periodStartInclusive = periodStartInclusive;
		return this;
	}

	public Instant getPeriodEndExclusive() {
		return periodEndExclusive;
	}

	public AdminTargetView setPeriodEndExclusive(Instant periodEndExclusive) {
		this.periodEndExclusive = periodEndExclusive;
		return this;
	}

	public TargetStatus getStatus() {
		return status;
	}

	public AdminTargetView setStatus(TargetStatus status) {
		this.status = status;
		return this;
	}

	public TargetProvisioningState getProvisioningState() {
		return provisioningState;
	}

	public AdminTargetView setProvisioningState(TargetProvisioningState provisioningState) {
		this.provisioningState = provisioningState;
		return this;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public AdminTargetView setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public AdminTargetView setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
		return this;
	}

	public long getVersion() {
		return version;
	}

	public AdminTargetView setVersion(long version) {
		this.version = version;
		return this;
	}

	private static Instant instant(JsonObject json, String key) {
		String value = json.getString(key);
		return value == null ? null : Instant.parse(value);
	}

	private static String string(Instant value) {
		return value == null ? null : value.toString();
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

	private static <E extends Enum<E>> E enumValue(JsonObject json, String key, Class<E> type) {
		String value = json.getString(key);
		return value == null ? null : Enum.valueOf(type, value);
	}
}
