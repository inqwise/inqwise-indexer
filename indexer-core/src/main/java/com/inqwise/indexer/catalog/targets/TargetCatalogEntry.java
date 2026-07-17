package com.inqwise.indexer.catalog.targets;

import java.time.Instant;
import java.util.Objects;

public record TargetCatalogEntry(
	Integer id,
	String uid,
	String targetName,
	String periodKey,
	Instant periodStartInclusive,
	Instant periodEndExclusive,
	TargetStatus status,
	TargetProvisioningState provisioningState,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public TargetCatalogEntry {
		id = Objects.requireNonNull(id, "id");
		uid = Objects.requireNonNull(uid, "uid");
		targetName = Objects.requireNonNull(targetName, "targetName");
		status = Objects.requireNonNull(status, "status");
		provisioningState = Objects.requireNonNull(provisioningState, "provisioningState");
	}
}
