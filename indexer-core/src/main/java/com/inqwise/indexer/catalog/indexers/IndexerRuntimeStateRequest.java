package com.inqwise.indexer.catalog.indexers;

import java.util.Objects;

public record IndexerRuntimeStateRequest(Integer indexerId, long expectedVersion) {
	public IndexerRuntimeStateRequest {
		Objects.requireNonNull(indexerId, "indexerId");
	}
}
