package com.inqwise.indexer.metadata;

import java.time.Instant;

public record TargetPeriod(
	TargetPeriodStrategy strategy,
	String key,
	Instant startInclusive,
	Instant endExclusive
) {
}
