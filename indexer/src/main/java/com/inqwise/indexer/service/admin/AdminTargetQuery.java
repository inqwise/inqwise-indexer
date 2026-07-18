package com.inqwise.indexer.service.admin;

import java.util.List;

import com.inqwise.indexer.catalog.targets.TargetCatalogQuery;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetQuery {
	public static final class Keys {
		public static final String IDS = "ids";
		public static final String TARGET_NAMES = "target_names";
		public static final String STATUSES = "statuses";
		public static final String PROVISIONING_STATES = "provisioning_states";

		private Keys() {
		}
	}

	private List<Integer> ids = List.of();
	private List<String> targetNames = List.of();
	private List<TargetStatus> statuses = List.of();
	private List<TargetProvisioningState> provisioningStates = List.of();

	public AdminTargetQuery() {
	}

	public AdminTargetQuery(JsonObject json) {
		this.ids = json.getJsonArray(Keys.IDS, new JsonArray()).stream()
			.map(Number.class::cast)
			.map(Number::intValue)
			.toList();
		this.targetNames = json.getJsonArray(Keys.TARGET_NAMES, new JsonArray()).stream()
			.map(String.class::cast)
			.toList();
		this.statuses = json.getJsonArray(Keys.STATUSES, new JsonArray()).stream()
			.map(String.class::cast)
			.map(TargetStatus::valueOf)
			.toList();
		this.provisioningStates = json.getJsonArray(Keys.PROVISIONING_STATES, new JsonArray()).stream()
			.map(String.class::cast)
			.map(TargetProvisioningState::valueOf)
			.toList();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.IDS, new JsonArray(ids))
			.put(Keys.TARGET_NAMES, new JsonArray(targetNames))
			.put(Keys.STATUSES, new JsonArray(statuses.stream().map(Enum::name).toList()))
			.put(Keys.PROVISIONING_STATES, new JsonArray(provisioningStates.stream().map(Enum::name).toList()));
	}

	public TargetCatalogQuery toCatalogQuery() {
		return TargetCatalogQuery.builder()
			.withIds(ids)
			.withTargetNames(targetNames)
			.withStatuses(statuses)
			.withProvisioningStates(provisioningStates)
			.build();
	}

	public List<Integer> getIds() {
		return ids;
	}

	public AdminTargetQuery setIds(List<Integer> ids) {
		this.ids = ids == null ? List.of() : List.copyOf(ids);
		return this;
	}

	public List<String> getTargetNames() {
		return targetNames;
	}

	public AdminTargetQuery setTargetNames(List<String> targetNames) {
		this.targetNames = targetNames == null ? List.of() : List.copyOf(targetNames);
		return this;
	}

	public List<TargetStatus> getStatuses() {
		return statuses;
	}

	public AdminTargetQuery setStatuses(List<TargetStatus> statuses) {
		this.statuses = statuses == null ? List.of() : List.copyOf(statuses);
		return this;
	}

	public List<TargetProvisioningState> getProvisioningStates() {
		return provisioningStates;
	}

	public AdminTargetQuery setProvisioningStates(List<TargetProvisioningState> provisioningStates) {
		this.provisioningStates = provisioningStates == null ? List.of() : List.copyOf(provisioningStates);
		return this;
	}
}
