package com.inqwise.indexer.metadata;

public record InsertPublication(
	String uid,
	Integer indexerId,
	Integer targetId,
	String targetName,
	String indexName,
	ReadinessState readinessState,
	String reason
) {
}
