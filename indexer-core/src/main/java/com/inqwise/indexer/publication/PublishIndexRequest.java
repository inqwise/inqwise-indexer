package com.inqwise.indexer.publication;

import java.util.Objects;

public record PublishIndexRequest(Integer indexerId, long expectedVersion) {
	public PublishIndexRequest {
		Objects.requireNonNull(indexerId, "indexerId");
	}
}
