package com.inqwise.indexer.metadata;

public record InsertTarget(
	String uid,
	Integer targetDefinitionId,
	String targetName,
	String periodKey,
	java.time.Instant periodStartInclusive,
	java.time.Instant periodEndExclusive,
	TargetStatus status
) {
	public InsertTarget(String uid, String targetName, TargetStatus status) {
		this(uid, null, targetName, null, null, null, status);
	}
}
