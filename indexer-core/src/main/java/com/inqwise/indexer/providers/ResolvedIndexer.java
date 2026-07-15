package com.inqwise.indexer.providers;

import java.util.Optional;

import com.inqwise.indexer.catalog.indexers.IndexerModel;

public interface ResolvedIndexer {
	IndexerModel model();

	default Optional<HotIndexerCapability> hotIndexer() {
		return Optional.empty();
	}
}
