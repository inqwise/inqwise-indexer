package com.inqwise.indexer.load;

public record UpdateIndexerLoadState(
	Integer loadIndexerId,
	IndexerLoadState state,
	long expectedVersion
) {
}
