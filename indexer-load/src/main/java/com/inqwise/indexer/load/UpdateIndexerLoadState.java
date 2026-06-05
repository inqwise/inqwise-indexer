package com.inqwise.indexer.load;

public record UpdateIndexerLoadState(
	Integer indexerId,
	IndexerLoadState state,
	long expectedVersion
) {
}
