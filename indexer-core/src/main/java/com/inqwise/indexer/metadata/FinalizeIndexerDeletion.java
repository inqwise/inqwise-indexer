package com.inqwise.indexer.metadata;

public record FinalizeIndexerDeletion(
	Integer indexerId,
	long expectedVersion
) {
}
