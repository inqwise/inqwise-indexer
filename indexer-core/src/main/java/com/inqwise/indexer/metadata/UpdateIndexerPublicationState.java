package com.inqwise.indexer.metadata;

public record UpdateIndexerPublicationState(
	Integer id,
	PublicationState publicationState,
	long expectedVersion
) {
}
