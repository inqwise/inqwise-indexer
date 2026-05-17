package com.inqwise.indexer.metadata;

public record DeleteIndexer(
	Integer id,
	long expectedVersion
) {
}
