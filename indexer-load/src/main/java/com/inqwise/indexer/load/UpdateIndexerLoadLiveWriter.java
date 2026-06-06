package com.inqwise.indexer.load;

public record UpdateIndexerLoadLiveWriter(
	Integer indexerId,
	Integer liveIndexerId,
	long expectedVersion
) {
}
