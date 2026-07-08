package com.inqwise.indexer.load;

import java.util.Objects;

public record StartLoadRequest(
	Integer indexerId,
	long expectedVersion
) {
	public StartLoadRequest {
		Objects.requireNonNull(indexerId, "indexerId");
	}
}
