package com.inqwise.indexer.publication;

import java.util.Objects;

public record RetireIndexRequest(Integer indexerId, long expectedVersion) {
	public RetireIndexRequest {
		Objects.requireNonNull(indexerId, "indexerId");
	}
}
