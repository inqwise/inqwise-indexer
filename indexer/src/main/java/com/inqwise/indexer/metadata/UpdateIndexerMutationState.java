package com.inqwise.indexer.metadata;

public record UpdateIndexerMutationState(
	Integer id,
	MutationState mutationState,
	long expectedVersion
) {
}
