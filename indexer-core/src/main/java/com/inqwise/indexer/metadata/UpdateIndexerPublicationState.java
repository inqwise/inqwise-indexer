package com.inqwise.indexer.metadata;

import com.inqwise.indexer.publication.PublicationState;

public record UpdateIndexerPublicationState(
	Integer id,
	PublicationState publicationState,
	long expectedVersion
) {
}
