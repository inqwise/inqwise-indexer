package com.inqwise.indexer.load.repository;

public record LoadIndexerReference(
	Integer id,
	Integer targetId,
	String indexName,
	Long version
) {
}
