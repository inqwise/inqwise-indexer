package com.inqwise.indexer.metadata;

import java.time.Instant;

public record TargetRecord(
	Integer id,
	String prefix,
	Integer targetDefinitionId,
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
