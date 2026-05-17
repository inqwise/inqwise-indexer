package com.inqwise.indexer.metadata;

public record UpdateIndexerRuntimeStatus(
	Integer id,
	IndexerRuntimeStatus runtimeStatus,
	long expectedVersion
) {
}
