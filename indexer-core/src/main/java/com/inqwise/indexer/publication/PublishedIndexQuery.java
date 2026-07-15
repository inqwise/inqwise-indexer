package com.inqwise.indexer.publication;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.catalog.targets.TargetNameValidator;

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
