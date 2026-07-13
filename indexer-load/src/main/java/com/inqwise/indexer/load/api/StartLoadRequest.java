package com.inqwise.indexer.load.api;

import java.util.Objects;

public record StartLoadRequest(
	Integer indexerId,
	long expectedVersion
) {
	public StartLoadRequest {
		Objects.requireNonNull(indexerId, "indexerId");
	}
}
