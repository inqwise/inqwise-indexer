package com.inqwise.indexer.metadata;

import java.time.Instant;

public record PublicationRecord(
	Integer id,
	String uid,
	Integer indexerId,
	Integer targetId,
	String targetName,
	String indexName,
	ReadinessState readinessState,
	String reason,
	Instant readyAt,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
}
