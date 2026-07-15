package com.inqwise.indexer.metadata;

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
	public InsertTarget(String prefix, String targetName, TargetStatus status) {
		this(
			prefix == null ? "test" : prefix,
			targetName,
			null,
			null,
			null,
			status == null ? TargetStatus.ACTIVE : status,
			TargetProvisioningState.READY
		);
	}
}
