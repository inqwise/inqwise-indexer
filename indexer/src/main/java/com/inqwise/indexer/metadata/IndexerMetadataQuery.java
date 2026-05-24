package com.inqwise.indexer.metadata;

import java.util.List;

import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;

public record IndexerMetadataQuery(
	List<Integer> ids,
	List<Integer> targetIds,
	List<IndexerType> types,
	List<IndexerStatus> statuses,
	List<IndexerProvisioningState> provisioningStates,
	List<IndexerRuntimeState> runtimeStates,
	List<PublicationState> publicationStates,
	List<MutationState> mutationStates
) {
	public IndexerMetadataQuery {
		ids = copy(ids);
		targetIds = copy(targetIds);
		types = copy(types);
		statuses = copy(statuses);
		provisioningStates = copy(provisioningStates);
		runtimeStates = copy(runtimeStates);
		publicationStates = copy(publicationStates);
		mutationStates = copy(mutationStates);
	}

	private static <T> List<T> copy(List<T> values) {
		return values == null ? List.of() : List.copyOf(values);
	}
}
