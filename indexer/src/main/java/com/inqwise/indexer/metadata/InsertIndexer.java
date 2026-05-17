package com.inqwise.indexer.metadata;

import com.inqwise.indexer.IndexerType;

public record InsertIndexer(
	String uid,
	Integer targetId,
	String targetName,
	String indexName,
	String queueName,
	IndexerType type,
	IndexerRuntimeStatus runtimeStatus,
	PublicationState publicationState,
	MutationState mutationState
) {
}
