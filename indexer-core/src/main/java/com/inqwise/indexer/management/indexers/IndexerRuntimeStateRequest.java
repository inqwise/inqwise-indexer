package com.inqwise.indexer.management.indexers;

import java.util.Objects;

public record IndexerRuntimeStateRequest(Integer indexerId, long expectedVersion) {
	public IndexerRuntimeStateRequest {
		Objects.requireNonNull(indexerId, "indexerId");
	}
}
