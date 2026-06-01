package com.inqwise.indexer.providers;

import java.util.Optional;

import com.inqwise.indexer.hot.HotIndexer;
import com.inqwise.indexer.metadata.IndexerRecord;

public interface ResolvedIndexer {
	IndexerRecord record();

	default Optional<HotIndexer> hotIndexer() {
		return Optional.empty();
	}
}
