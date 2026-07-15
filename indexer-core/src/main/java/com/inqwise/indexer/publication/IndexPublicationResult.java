package com.inqwise.indexer.publication;

import java.util.Objects;

public record IndexPublicationResult(
	Integer indexerId,
	Integer targetId,
	PublicationState publicationState,
	long version
) {
	public IndexPublicationResult {
		indexerId = Objects.requireNonNull(indexerId, "indexerId");
		targetId = Objects.requireNonNull(targetId, "targetId");
		publicationState = Objects.requireNonNull(publicationState, "publicationState");
	}
}
