package com.inqwise.indexer.metadata;

import java.time.Instant;
import java.util.Objects;

public record PublishedIndexQuery(
	String targetName,
	Instant fromInclusive,
	Instant toExclusive
) {
	public PublishedIndexQuery {
		TargetNameValidator.requireTargetName(targetName);
		Objects.requireNonNull(fromInclusive, "fromInclusive");
		Objects.requireNonNull(toExclusive, "toExclusive");

		if (!fromInclusive.isBefore(toExclusive)) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
	}
}
