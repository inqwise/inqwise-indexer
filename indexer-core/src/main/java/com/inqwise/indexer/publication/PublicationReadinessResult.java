package com.inqwise.indexer.publication;

import java.util.Objects;

public record PublicationReadinessResult(
	Integer publicationId,
	Integer indexerId,
	ReadinessState readinessState,
	long version
) {
	public PublicationReadinessResult {
		publicationId = Objects.requireNonNull(publicationId, "publicationId");
		indexerId = Objects.requireNonNull(indexerId, "indexerId");
		readinessState = Objects.requireNonNull(readinessState, "readinessState");
	}
}
