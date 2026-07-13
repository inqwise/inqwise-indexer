package com.inqwise.indexer.load.catalog;

import com.inqwise.indexer.load.api.IndexerLoadRecord;

import io.vertx.core.Future;

public interface LoadCreationCatalog {
	Future<LoadCreationTarget> getReadyTarget(Integer targetId);

	Future<LoadCreatedIndexer> createLoadWriter(LoadCreationTarget target);

	Future<LoadCreatedIndexer> createImmediateLiveWriter(
		LoadCreationTarget target,
		LoadCreatedIndexer loadWriter
	);

	Future<LoadStartContext> prepareStart(IndexerLoadRecord load);
}
