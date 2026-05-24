package com.inqwise.indexer.metadata;

import java.util.List;

public record TargetMetadataQuery(
	List<Integer> ids,
	List<Integer> targetDefinitionIds,
	List<TargetStatus> statuses,
	List<TargetProvisioningState> provisioningStates
) {
	public TargetMetadataQuery {
		ids = copy(ids);
		targetDefinitionIds = copy(targetDefinitionIds);
		statuses = copy(statuses);
		provisioningStates = copy(provisioningStates);
	}

	private static <T> List<T> copy(List<T> values) {
		return values == null ? List.of() : List.copyOf(values);
	}
}
