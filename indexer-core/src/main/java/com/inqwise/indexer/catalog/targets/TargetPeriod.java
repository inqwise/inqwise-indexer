package com.inqwise.indexer.catalog.targets;

import java.time.Instant;

public record TargetPeriod(
	TargetPeriodStrategy strategy,
	String key,
	Instant startInclusive,
	Instant endExclusive
) {
}
