package com.inqwise.indexer.metadata;

public record InsertTarget(
	String prefix,
	Integer targetDefinitionId,
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
			null,
			targetName,
			null,
			null,
			null,
			status == null ? TargetStatus.ACTIVE : status,
			TargetProvisioningState.READY
		);
	}
}
