package com.inqwise.indexer.load;

import java.time.Instant;

public record UpdateIndexerLoadBarrier(
	Integer loadIndexerId,
	String barrierId,
	Instant barrierTimestamp,
	Instant reachedAt,
	long expectedVersion
) {
}
