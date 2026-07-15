package com.inqwise.indexer.metadata;

import java.time.Instant;

import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

public record TargetRecord(
	Integer id,
	String prefix,
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
	public String uid() {
		return MetadataUid.toToken(prefix, id);
	}
}
