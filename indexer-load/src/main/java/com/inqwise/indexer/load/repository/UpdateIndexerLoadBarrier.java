package com.inqwise.indexer.load.repository;

import java.time.Instant;

public record UpdateIndexerLoadBarrier(
	Integer indexerId,
	String barrierId,
	Instant barrierTimestamp,
	Instant reachedAt,
	long expectedVersion
) {
}
