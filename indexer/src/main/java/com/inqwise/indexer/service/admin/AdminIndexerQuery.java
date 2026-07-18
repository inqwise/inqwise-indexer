package com.inqwise.indexer.service.admin;

import java.util.List;

import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.metadata.IndexerMetadataQuery;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminIndexerQuery {
	public static final class Keys {
		public static final String IDS = "ids";
		public static final String TARGET_IDS = "target_ids";
		public static final String TYPES = "types";
		public static final String ROLES = "roles";
		public static final String STATUSES = "statuses";
		public static final String PROVISIONING_STATES = "provisioning_states";
		public static final String RUNTIME_STATES = "runtime_states";
		public static final String PUBLICATION_STATES = "publication_states";
		public static final String MUTATION_STATES = "mutation_states";

		private Keys() {
		}
	}

	private List<Integer> ids = List.of();
	private List<Integer> targetIds = List.of();
	private List<IndexerType> types = List.of();
	private List<IndexerRole> roles = List.of();
	private List<IndexerStatus> statuses = List.of();
	private List<IndexerProvisioningState> provisioningStates = List.of();
	private List<IndexerRuntimeState> runtimeStates = List.of();
	private List<PublicationState> publicationStates = List.of();
	private List<MutationState> mutationStates = List.of();

	public AdminIndexerQuery() {
	}

	public AdminIndexerQuery(JsonObject json) {
		this.ids = integers(json, Keys.IDS);
		this.targetIds = integers(json, Keys.TARGET_IDS);
		this.types = enums(json, Keys.TYPES, IndexerType.class);
		this.roles = enums(json, Keys.ROLES, IndexerRole.class);
		this.statuses = enums(json, Keys.STATUSES, IndexerStatus.class);
		this.provisioningStates = enums(json, Keys.PROVISIONING_STATES, IndexerProvisioningState.class);
		this.runtimeStates = enums(json, Keys.RUNTIME_STATES, IndexerRuntimeState.class);
		this.publicationStates = enums(json, Keys.PUBLICATION_STATES, PublicationState.class);
		this.mutationStates = enums(json, Keys.MUTATION_STATES, MutationState.class);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.IDS, new JsonArray(ids))
			.put(Keys.TARGET_IDS, new JsonArray(targetIds))
			.put(Keys.TYPES, enumArray(types))
			.put(Keys.ROLES, enumArray(roles))
			.put(Keys.STATUSES, enumArray(statuses))
			.put(Keys.PROVISIONING_STATES, enumArray(provisioningStates))
			.put(Keys.RUNTIME_STATES, enumArray(runtimeStates))
			.put(Keys.PUBLICATION_STATES, enumArray(publicationStates))
			.put(Keys.MUTATION_STATES, enumArray(mutationStates));
	}

	public IndexerMetadataQuery toMetadataQuery() {
		return IndexerMetadataQuery.builder()
			.withIds(ids)
			.withTargetIds(targetIds)
			.withTypes(types)
			.withRoles(roles)
			.withStatuses(statuses)
			.withProvisioningStates(provisioningStates)
			.withRuntimeStates(runtimeStates)
			.withPublicationStates(publicationStates)
			.withMutationStates(mutationStates)
			.build();
	}

	public List<Integer> getIds() {
		return ids;
	}

	public AdminIndexerQuery setIds(List<Integer> ids) {
		this.ids = ids == null ? List.of() : List.copyOf(ids);
		return this;
	}

	public List<Integer> getTargetIds() {
		return targetIds;
	}

	public AdminIndexerQuery setTargetIds(List<Integer> targetIds) {
		this.targetIds = targetIds == null ? List.of() : List.copyOf(targetIds);
		return this;
	}

	public List<IndexerType> getTypes() {
		return types;
	}

	public AdminIndexerQuery setTypes(List<IndexerType> types) {
		this.types = types == null ? List.of() : List.copyOf(types);
		return this;
	}

	public List<IndexerRole> getRoles() {
		return roles;
	}

	public AdminIndexerQuery setRoles(List<IndexerRole> roles) {
		this.roles = roles == null ? List.of() : List.copyOf(roles);
		return this;
	}

	public List<IndexerStatus> getStatuses() {
		return statuses;
	}

	public AdminIndexerQuery setStatuses(List<IndexerStatus> statuses) {
		this.statuses = statuses == null ? List.of() : List.copyOf(statuses);
		return this;
	}

	public List<IndexerProvisioningState> getProvisioningStates() {
		return provisioningStates;
	}

	public AdminIndexerQuery setProvisioningStates(List<IndexerProvisioningState> provisioningStates) {
		this.provisioningStates = provisioningStates == null ? List.of() : List.copyOf(provisioningStates);
		return this;
	}

	public List<IndexerRuntimeState> getRuntimeStates() {
		return runtimeStates;
	}

	public AdminIndexerQuery setRuntimeStates(List<IndexerRuntimeState> runtimeStates) {
		this.runtimeStates = runtimeStates == null ? List.of() : List.copyOf(runtimeStates);
		return this;
	}

	public List<PublicationState> getPublicationStates() {
		return publicationStates;
	}

	public AdminIndexerQuery setPublicationStates(List<PublicationState> publicationStates) {
		this.publicationStates = publicationStates == null ? List.of() : List.copyOf(publicationStates);
		return this;
	}

	public List<MutationState> getMutationStates() {
		return mutationStates;
	}

	public AdminIndexerQuery setMutationStates(List<MutationState> mutationStates) {
		this.mutationStates = mutationStates == null ? List.of() : List.copyOf(mutationStates);
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

	private static JsonArray enumArray(List<? extends Enum<?>> values) {
		return new JsonArray(values.stream().map(Enum::name).toList());
	}
}
