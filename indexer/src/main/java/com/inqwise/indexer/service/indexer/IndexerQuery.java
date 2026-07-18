package com.inqwise.indexer.service.indexer;

import java.util.List;

import com.inqwise.indexer.catalog.indexers.IndexerCatalogQuery;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerQuery {
	private List<Integer> ids = List.of();
	private List<Integer> targetIds = List.of();
	private List<IndexerType> types = List.of();
	private List<IndexerRole> roles = List.of();
	private List<IndexerStatus> statuses = List.of();
	private List<IndexerProvisioningState> provisioningStates = List.of();
	private List<IndexerRuntimeState> runtimeStates = List.of();
	private List<MutationState> mutationStates = List.of();

	public IndexerQuery() {
	}

	public IndexerQuery(JsonObject json) {
		ids = integers(json, "ids");
		targetIds = integers(json, "target_ids");
		types = enums(json, "types", IndexerType.class);
		roles = enums(json, "roles", IndexerRole.class);
		statuses = enums(json, "statuses", IndexerStatus.class);
		provisioningStates = enums(json, "provisioning_states", IndexerProvisioningState.class);
		runtimeStates = enums(json, "runtime_states", IndexerRuntimeState.class);
		mutationStates = enums(json, "mutation_states", MutationState.class);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("ids", new JsonArray(ids))
			.put("target_ids", new JsonArray(targetIds))
			.put("types", names(types))
			.put("roles", names(roles))
			.put("statuses", names(statuses))
			.put("provisioning_states", names(provisioningStates))
			.put("runtime_states", names(runtimeStates))
			.put("mutation_states", names(mutationStates));
	}

	IndexerCatalogQuery toCatalogQuery() {
		return new IndexerCatalogQuery(
			ids,
			targetIds,
			types,
			roles,
			statuses,
			provisioningStates,
			runtimeStates,
			mutationStates
		);
	}

	public List<Integer> getIds() {
		return ids;
	}

	public IndexerQuery setIds(List<Integer> values) {
		ids = copy(values);
		return this;
	}

	public List<Integer> getTargetIds() {
		return targetIds;
	}

	public IndexerQuery setTargetIds(List<Integer> values) {
		targetIds = copy(values);
		return this;
	}

	public List<IndexerType> getTypes() {
		return types;
	}

	public IndexerQuery setTypes(List<IndexerType> values) {
		types = copy(values);
		return this;
	}

	public List<IndexerRole> getRoles() {
		return roles;
	}

	public IndexerQuery setRoles(List<IndexerRole> values) {
		roles = copy(values);
		return this;
	}

	public List<IndexerStatus> getStatuses() {
		return statuses;
	}

	public IndexerQuery setStatuses(List<IndexerStatus> values) {
		statuses = copy(values);
		return this;
	}

	public List<IndexerProvisioningState> getProvisioningStates() {
		return provisioningStates;
	}

	public IndexerQuery setProvisioningStates(List<IndexerProvisioningState> values) {
		provisioningStates = copy(values);
		return this;
	}

	public List<IndexerRuntimeState> getRuntimeStates() {
		return runtimeStates;
	}

	public IndexerQuery setRuntimeStates(List<IndexerRuntimeState> values) {
		runtimeStates = copy(values);
		return this;
	}

	public List<MutationState> getMutationStates() {
		return mutationStates;
	}

	public IndexerQuery setMutationStates(List<MutationState> values) {
		mutationStates = copy(values);
		return this;
	}

	private static List<Integer> integers(JsonObject json, String key) {
		return json.getJsonArray(key, new JsonArray()).stream()
			.map(Number.class::cast)
			.map(Number::intValue)
			.toList();
	}

	private static <E extends Enum<E>> List<E> enums(JsonObject json, String key, Class<E> type) {
		return json.getJsonArray(key, new JsonArray()).stream()
			.map(String.class::cast)
			.map(value -> Enum.valueOf(type, value))
			.toList();
	}

	private static JsonArray names(List<? extends Enum<?>> values) {
		return new JsonArray(values.stream().map(Enum::name).toList());
	}

	private static <T> List<T> copy(List<T> values) {
		return values == null ? List.of() : List.copyOf(values);
	}
}
