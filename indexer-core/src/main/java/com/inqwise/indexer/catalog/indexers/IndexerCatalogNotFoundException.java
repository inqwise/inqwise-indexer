package com.inqwise.indexer.catalog.indexers;

import java.util.Objects;

public final class IndexerCatalogNotFoundException extends RuntimeException {
	private final Integer indexerId;

	public IndexerCatalogNotFoundException(Integer indexerId) {
		super("Indexer not found: " + Objects.requireNonNull(indexerId, "indexerId"));
		this.indexerId = indexerId;
	}

	public Integer indexerId() {
		return indexerId;
	}
}
