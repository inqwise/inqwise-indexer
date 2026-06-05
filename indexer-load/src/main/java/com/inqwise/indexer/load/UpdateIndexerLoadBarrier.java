package com.inqwise.indexer.load;

import java.time.Instant;

public record UpdateIndexerLoadBarrier(
	Integer indexerId,
	String barrierId,
	Instant barrierTimestamp,
	Instant reachedAt,
	long expectedVersion
) {
}
