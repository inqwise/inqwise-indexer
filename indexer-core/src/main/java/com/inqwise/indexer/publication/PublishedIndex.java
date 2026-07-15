package com.inqwise.indexer.publication;

public record PublishedIndex(
	Integer indexerId,
	Integer targetId,
	String indexName
) {
}
