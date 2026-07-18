package com.inqwise.indexer.service.target;

import java.util.List;

import com.inqwise.indexer.catalog.targets.TargetCatalogQuery;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetQuery {
	private List<Integer> ids = List.of();
	private List<String> targetNames = List.of();
	private List<TargetStatus> statuses = List.of();
	private List<TargetProvisioningState> provisioningStates = List.of();

	public TargetQuery() {
	}

	public TargetQuery(JsonObject json) {
		ids = json.getJsonArray("ids", new JsonArray()).stream()
			.map(Number.class::cast)
			.map(Number::intValue)
			.toList();
		targetNames = strings(json, "target_names");
		statuses = enums(json, "statuses", TargetStatus.class);
		provisioningStates = enums(
			json,
			"provisioning_states",
			TargetProvisioningState.class
		);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("ids", new JsonArray(ids))
			.put("target_names", new JsonArray(targetNames))
			.put("statuses", names(statuses))
			.put("provisioning_states", names(provisioningStates));
	}

	TargetCatalogQuery toCatalogQuery() {
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

	public TargetQuery setIds(List<Integer> values) {
		ids = copy(values);
		return this;
	}

	public List<String> getTargetNames() {
		return targetNames;
	}

	public TargetQuery setTargetNames(List<String> values) {
		targetNames = copy(values);
		return this;
	}

	public List<TargetStatus> getStatuses() {
		return statuses;
	}

	public TargetQuery setStatuses(List<TargetStatus> values) {
		statuses = copy(values);
		return this;
	}

	public List<TargetProvisioningState> getProvisioningStates() {
		return provisioningStates;
	}

	public TargetQuery setProvisioningStates(List<TargetProvisioningState> values) {
		provisioningStates = copy(values);
		return this;
	}

	private static List<String> strings(JsonObject json, String key) {
		return json.getJsonArray(key, new JsonArray()).stream()
			.map(String.class::cast)
			.toList();
	}

	private static <E extends Enum<E>> List<E> enums(JsonObject json, String key, Class<E> type) {
		return strings(json, key).stream().map(value -> Enum.valueOf(type, value)).toList();
	}

	private static JsonArray names(List<? extends Enum<?>> values) {
		return new JsonArray(values.stream().map(Enum::name).toList());
	}

	private static <T> List<T> copy(List<T> values) {
		return values == null ? List.of() : List.copyOf(values);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<Integer> ids = List.of();
		private List<String> targetNames = List.of();
		private List<TargetStatus> statuses = List.of();
		private List<TargetProvisioningState> provisioningStates = List.of();

		private Builder() {
		}

		public Builder withIds(List<Integer> values) {
			ids = copy(values);
			return this;
		}

		public Builder withTargetNames(List<String> values) {
			targetNames = copy(values);
			return this;
		}

		public Builder withStatuses(List<TargetStatus> values) {
			statuses = copy(values);
			return this;
		}

		public Builder withProvisioningStates(List<TargetProvisioningState> values) {
			provisioningStates = copy(values);
			return this;
		}

		public TargetQuery build() {
			return new TargetQuery()
				.setIds(ids)
				.setTargetNames(targetNames)
				.setStatuses(statuses)
				.setProvisioningStates(provisioningStates);
		}
	}
}
