package com.inqwise.indexer.providers;

import java.util.List;

import com.inqwise.indexer.catalog.indexers.IndexerModel;

public record PreparedIndexers(
	List<IndexerModel> indexers,
	boolean metadataChanged
) {
	public PreparedIndexers {
		indexers = indexers == null ? List.of() : List.copyOf(indexers);
	}
}
