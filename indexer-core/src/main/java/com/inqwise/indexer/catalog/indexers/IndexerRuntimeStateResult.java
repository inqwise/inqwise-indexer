package com.inqwise.indexer.catalog.indexers;

import java.util.Objects;

public record IndexerRuntimeStateResult(
	Integer indexerId,
	Integer targetId,
	IndexerRuntimeState runtimeState,
	long version
) {
	public IndexerRuntimeStateResult {
		indexerId = Objects.requireNonNull(indexerId, "indexerId");
		targetId = Objects.requireNonNull(targetId, "targetId");
		runtimeState = Objects.requireNonNull(runtimeState, "runtimeState");
	}
}
