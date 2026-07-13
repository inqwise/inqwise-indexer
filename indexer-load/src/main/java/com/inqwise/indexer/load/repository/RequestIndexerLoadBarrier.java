package com.inqwise.indexer.load.repository;

import java.time.Instant;

public record RequestIndexerLoadBarrier(
	Integer indexerId,
	String barrierId,
	Instant barrierTimestamp,
	long expectedVersion
) {
}
