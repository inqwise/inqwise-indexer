package com.inqwise.indexer.catalog.indexers;

public record MarkIndexerDeletingRequest(
	Integer indexerId,
	long expectedVersion
) {
}
