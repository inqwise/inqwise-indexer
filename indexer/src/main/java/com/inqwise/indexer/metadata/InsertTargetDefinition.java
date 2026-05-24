package com.inqwise.indexer.metadata;

public record InsertTargetDefinition(
	String prefix,
	String targetName,
	TargetPeriodStrategy periodStrategy,
	TargetStatus status
) {
}
