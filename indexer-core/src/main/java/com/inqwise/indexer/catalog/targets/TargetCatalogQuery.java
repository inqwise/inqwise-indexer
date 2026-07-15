package com.inqwise.indexer.catalog.targets;

import java.util.List;

public record TargetCatalogQuery(
	List<Integer> ids,
	List<String> targetNames,
	List<TargetStatus> statuses,
	List<TargetProvisioningState> provisioningStates
) {
	public TargetCatalogQuery {
		ids = copy(ids);
		targetNames = copy(targetNames);
		statuses = copy(statuses);
		provisioningStates = copy(provisioningStates);
	}

	private static <T> List<T> copy(List<T> values) {
		return values == null ? List.of() : List.copyOf(values);
	}
}
