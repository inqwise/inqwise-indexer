package com.inqwise.indexer.providers;

import java.util.Optional;

import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.hot.HotIndexer;

public interface ResolvedIndexer {
	IndexerModel model();

	default Optional<HotIndexer> hotIndexer() {
		return Optional.empty();
	}
}
