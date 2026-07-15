package com.inqwise.indexer.metadata;

import java.time.Instant;

import com.inqwise.indexer.publication.ReadinessState;

public record PublicationRecord(
	Integer id,
	String prefix,
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
	public String uid() {
		return MetadataUid.toToken(prefix, id);
	}
}
