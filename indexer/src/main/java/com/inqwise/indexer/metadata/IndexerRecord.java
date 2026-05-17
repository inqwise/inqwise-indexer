package com.inqwise.indexer.metadata;

import java.time.Instant;

import com.inqwise.indexer.IndexerType;

public record IndexerRecord(
	Integer id,
	String uid,
	Integer targetId,
	String targetName,
	String indexName,
	String queueName,
	IndexerType type,
	IndexerRuntimeStatus runtimeStatus,
	PublicationState publicationState,
	MutationState mutationState,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
}
