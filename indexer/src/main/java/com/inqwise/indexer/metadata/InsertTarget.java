package com.inqwise.indexer.metadata;

public record InsertTarget(
	String uid,
	String targetName,
	TargetStatus status
) {
}
