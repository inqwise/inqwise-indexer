package com.inqwise.indexer.load.catalog;

public record LoadCreatedIndexer(
	Integer id,
	Integer targetId,
	String prefix,
	String indexName,
	String queueName,
	Long version
) {
}
