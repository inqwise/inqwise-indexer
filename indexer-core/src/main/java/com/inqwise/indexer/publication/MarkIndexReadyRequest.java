package com.inqwise.indexer.publication;

import java.util.Objects;

public record MarkIndexReadyRequest(
	Integer publicationId,
	String reason,
	long expectedVersion
) {
	public MarkIndexReadyRequest {
		Objects.requireNonNull(publicationId, "publicationId");
	}
}
