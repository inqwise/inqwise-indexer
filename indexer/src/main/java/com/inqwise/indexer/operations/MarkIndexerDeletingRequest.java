package com.inqwise.indexer.operations;

public record MarkIndexerDeletingRequest(
	Integer indexerId,
	long expectedVersion
) {
}
