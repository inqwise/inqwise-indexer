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
}
