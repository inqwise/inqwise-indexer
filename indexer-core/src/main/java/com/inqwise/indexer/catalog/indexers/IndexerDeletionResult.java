package com.inqwise.indexer.catalog.indexers;

import java.util.Objects;

public record IndexerDeletionResult(
	Integer indexerId,
	Integer targetId,
	MutationState mutationState,
	IndexerRuntimeState runtimeState,
	long version
) {
	public IndexerDeletionResult {
		indexerId = Objects.requireNonNull(indexerId, "indexerId");
		targetId = Objects.requireNonNull(targetId, "targetId");
		mutationState = Objects.requireNonNull(mutationState, "mutationState");
		runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
	}
}
