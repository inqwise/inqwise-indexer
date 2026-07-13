package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadState;


import java.time.Instant;

public record IndexerLoadCompletion(
	Integer indexerId,
	IndexerLoadState terminalState,
	long terminalVersion,
	Instant completedAt
) {
}
