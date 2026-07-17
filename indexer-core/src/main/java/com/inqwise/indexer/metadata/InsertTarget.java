package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

public record InsertTarget(
	String prefix,
	String targetName,
	String periodKey,
	java.time.Instant periodStartInclusive,
	java.time.Instant periodEndExclusive,
	TargetStatus status,
	TargetProvisioningState provisioningState
) {
	public InsertTarget {
		Objects.requireNonNull(prefix, "prefix");
	}
}
