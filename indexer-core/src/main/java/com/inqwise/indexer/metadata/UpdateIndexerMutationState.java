package com.inqwise.indexer.metadata;

import com.inqwise.indexer.catalog.indexers.MutationState;

public record UpdateIndexerMutationState(
	Integer id,
	MutationState mutationState,
	long expectedVersion
) {
}
