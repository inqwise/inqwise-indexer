package com.inqwise.indexer.load;

import java.time.Instant;

public record IndexerLoadCompletion(
	Integer indexerId,
	IndexerLoadState terminalState,
	long terminalVersion,
	Instant completedAt
) {
}
