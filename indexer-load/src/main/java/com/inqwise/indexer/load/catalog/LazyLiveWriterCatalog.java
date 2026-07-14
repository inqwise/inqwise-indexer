package com.inqwise.indexer.load.catalog;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.catalog.indexers.IndexerModel;

import io.vertx.core.Future;

public interface LazyLiveWriterCatalog {
	Future<IndexerModel> getLiveWriter(Integer liveWriterId);

	Future<IndexerModel> createAttachedLiveWriter(
		IndexerLoadRecord load,
		IndexerModel loadIndexer
	);
}
