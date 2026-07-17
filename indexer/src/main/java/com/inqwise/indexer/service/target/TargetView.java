package com.inqwise.indexer.service.target;

import java.time.Instant;

import com.inqwise.indexer.catalog.targets.TargetCatalogEntry;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetView {
	private JsonObject value;

	public TargetView() {
		value = new JsonObject();
	}

	public TargetView(JsonObject json) {
		value = json.copy();
	}

	public JsonObject toJson() {
		return value.copy();
	}

	public static TargetView from(TargetCatalogEntry entry) {
		return new TargetView(new JsonObject()
			.put("id", entry.id())
			.put("uid", entry.uid())
			.put("target_name", entry.targetName())
			.put("period_key", entry.periodKey())
			.put("period_start_inclusive", string(entry.periodStartInclusive()))
			.put("period_end_exclusive", string(entry.periodEndExclusive()))
			.put("status", entry.status().name())
			.put("provisioning_state", entry.provisioningState().name())
			.put("created_at", string(entry.createdAt()))
			.put("updated_at", string(entry.updatedAt()))
			.put("version", entry.version()));
	}

	public Integer getId() {
		return value.getInteger("id");
	}

	public String getUid() {
		return value.getString("uid");
	}

	public String getTargetName() {
		return value.getString("target_name");
	}

	public TargetStatus getStatus() {
		return enumValue("status", TargetStatus.class);
	}

	public TargetProvisioningState getProvisioningState() {
		return enumValue("provisioning_state", TargetProvisioningState.class);
	}

	public long getVersion() {
		return value.getLong("version", 0L);
	}

	private <E extends Enum<E>> E enumValue(String key, Class<E> type) {
		String current = value.getString(key);
		return current == null ? null : Enum.valueOf(type, current);
	}

	private static String string(Instant value) {
		return value == null ? null : value.toString();
	}
}
