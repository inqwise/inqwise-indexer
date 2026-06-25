package com.inqwise.indexer.metadata;

public record InsertPublication(
	String prefix,
	Integer indexerId,
	Integer targetId,
	String targetName,
	String indexName,
	ReadinessState readinessState,
	String reason
) {
	public InsertPublication {
		prefix = prefix == null ? "test" : prefix;
	}
}
