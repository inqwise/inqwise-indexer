package com.inqwise.indexer.metadata;

import java.time.Instant;

public record TargetRecord(
	Integer id,
	String uid,
	String targetName,
	TargetStatus status,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
}
