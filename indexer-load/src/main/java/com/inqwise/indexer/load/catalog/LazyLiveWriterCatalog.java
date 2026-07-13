package com.inqwise.indexer.load.catalog;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

public interface LazyLiveWriterCatalog {
	Future<IndexerRecord> getLiveWriter(Integer liveWriterId);

	Future<IndexerRecord> createAttachedLiveWriter(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer
	);
}
