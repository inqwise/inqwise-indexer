package com.inqwise.indexer.load;

import java.time.Instant;

public record InsertIndexerLoad(
	Integer loadIndexerId,
	Integer liveIndexerId,
	IndexerLoadState state,
	Instant reloadStartAt,
	Instant liveReplayFrom,
	boolean reviewRequired
) {
}
