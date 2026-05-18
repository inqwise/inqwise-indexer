package com.inqwise.indexer.metadata;

import java.time.Instant;

public record TargetDefinitionRecord(
	Integer id,
	String uid,
	String targetName,
	TargetPeriodStrategy periodStrategy,
	TargetStatus status,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
}
