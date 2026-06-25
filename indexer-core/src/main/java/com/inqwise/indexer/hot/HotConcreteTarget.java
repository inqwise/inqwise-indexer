package com.inqwise.indexer.hot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record HotConcreteTarget(
	Integer targetId,
	String targetName,
	String periodKey,
	Instant periodStartInclusive,
	Instant periodEndExclusive,
	List<HotIndexer> liveWriters
) {
	public HotConcreteTarget {
		Objects.requireNonNull(targetId, "targetId");
		Objects.requireNonNull(targetName, "targetName");
		liveWriters = List.copyOf(Objects.requireNonNull(liveWriters, "liveWriters"));
	}
}
