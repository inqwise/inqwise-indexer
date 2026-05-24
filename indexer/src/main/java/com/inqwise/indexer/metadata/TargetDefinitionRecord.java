package com.inqwise.indexer.metadata;

import java.time.Instant;

public record TargetDefinitionRecord(
	Integer id,
	String prefix,
	String targetName,
	TargetPeriodStrategy periodStrategy,
	TargetStatus status,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
	public String uid() {
		return MetadataUid.toToken(prefix, id);
	}
}
