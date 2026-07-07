package com.inqwise.indexer.load;

import java.util.Objects;

public record CancelLoadRequest(Integer indexerId, String reason, long expectedVersion) {
	public CancelLoadRequest {
		Objects.requireNonNull(indexerId, "indexerId");
		if (expectedVersion < 0) {
			throw new IllegalArgumentException("expectedVersion must not be negative");
		}
	}
}
