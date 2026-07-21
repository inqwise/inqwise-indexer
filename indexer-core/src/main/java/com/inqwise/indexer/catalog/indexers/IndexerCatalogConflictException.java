package com.inqwise.indexer.catalog.indexers;

import java.util.Objects;

public final class IndexerCatalogConflictException extends RuntimeException {
	private final Integer indexerId;

	public IndexerCatalogConflictException(Integer indexerId, String details) {
		super("Indexer conflict for id " + Objects.requireNonNull(indexerId, "indexerId")
			+ ": " + Objects.requireNonNull(details, "details"));
		this.indexerId = indexerId;
	}

	public Integer indexerId() {
		return indexerId;
	}
}
