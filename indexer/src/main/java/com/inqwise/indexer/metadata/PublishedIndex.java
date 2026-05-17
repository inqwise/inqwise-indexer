package com.inqwise.indexer.metadata;

public record PublishedIndex(
	Integer indexerId,
	Integer targetId,
	String indexName
) {
}
