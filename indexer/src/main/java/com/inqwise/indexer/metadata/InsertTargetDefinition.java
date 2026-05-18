package com.inqwise.indexer.metadata;

public record InsertTargetDefinition(
	String uid,
	String targetName,
	TargetPeriodStrategy periodStrategy,
	TargetStatus status
) {
}
