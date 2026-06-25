package com.inqwise.indexer.providers;

import java.util.List;

import com.inqwise.indexer.metadata.IndexerRecord;

public record PreparedIndexers(
	List<IndexerRecord> indexers,
	boolean metadataChanged
) {
	public PreparedIndexers {
		indexers = indexers == null ? List.of() : List.copyOf(indexers);
	}
}
