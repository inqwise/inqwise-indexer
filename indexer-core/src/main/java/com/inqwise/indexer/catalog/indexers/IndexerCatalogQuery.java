package com.inqwise.indexer.catalog.indexers;

import java.util.List;

public record IndexerCatalogQuery(
	List<Integer> ids,
	List<Integer> targetIds,
	List<IndexerType> types,
	List<IndexerRole> roles,
	List<IndexerStatus> statuses,
	List<IndexerProvisioningState> provisioningStates,
	List<IndexerRuntimeState> runtimeStates,
	List<MutationState> mutationStates
) {
	public IndexerCatalogQuery {
		ids = copy(ids);
		targetIds = copy(targetIds);
		types = copy(types);
		roles = copy(roles);
		statuses = copy(statuses);
		provisioningStates = copy(provisioningStates);
		runtimeStates = copy(runtimeStates);
		mutationStates = copy(mutationStates);
	}

	private static <T> List<T> copy(List<T> values) {
		return values == null ? List.of() : List.copyOf(values);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<Integer> ids = List.of();
		private List<Integer> targetIds = List.of();
		private List<IndexerType> types = List.of();
		private List<IndexerRole> roles = List.of();
		private List<IndexerStatus> statuses = List.of();
		private List<IndexerProvisioningState> provisioningStates = List.of();
		private List<IndexerRuntimeState> runtimeStates = List.of();
		private List<MutationState> mutationStates = List.of();

		private Builder() {
		}

		public Builder withIds(List<Integer> values) {
			ids = copy(values);
			return this;
		}

		public Builder withTargetIds(List<Integer> values) {
			targetIds = copy(values);
			return this;
		}

		public Builder withTypes(List<IndexerType> values) {
			types = copy(values);
			return this;
		}

		public Builder withRoles(List<IndexerRole> values) {
			roles = copy(values);
			return this;
		}

		public Builder withStatuses(List<IndexerStatus> values) {
			statuses = copy(values);
			return this;
		}

		public Builder withProvisioningStates(List<IndexerProvisioningState> values) {
			provisioningStates = copy(values);
			return this;
		}

		public Builder withRuntimeStates(List<IndexerRuntimeState> values) {
			runtimeStates = copy(values);
			return this;
		}

		public Builder withMutationStates(List<MutationState> values) {
			mutationStates = copy(values);
			return this;
		}

		public IndexerCatalogQuery build() {
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
	}
}
