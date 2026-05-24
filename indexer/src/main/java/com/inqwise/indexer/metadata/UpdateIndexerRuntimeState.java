package com.inqwise.indexer.metadata;

import com.inqwise.indexer.IndexerRuntimeState;

public record UpdateIndexerRuntimeState(
	Integer id,
	IndexerRuntimeState runtimeState,
	long expectedVersion
) {
}
