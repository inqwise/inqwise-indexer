package com.inqwise.indexer.load.api;

import java.util.Objects;

public record RecoverCreatedLoadRequest(Integer indexerId, long expectedVersion) {
	public RecoverCreatedLoadRequest {
		Objects.requireNonNull(indexerId, "indexerId");
		if (expectedVersion < 0) {
			throw new IllegalArgumentException("expectedVersion must not be negative");
		}
	}
}
