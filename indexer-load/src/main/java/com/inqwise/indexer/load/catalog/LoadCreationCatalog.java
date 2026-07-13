package com.inqwise.indexer.load.catalog;

import com.inqwise.indexer.load.api.IndexerLoadRecord;

import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.TargetRecord;

import io.vertx.core.Future;

public interface LoadCreationCatalog {
	Future<TargetRecord> getReadyTarget(Integer targetId);

	Future<IndexerRecord> createLoadWriter(TargetRecord target);

	Future<IndexerRecord> createImmediateLiveWriter(
		TargetRecord target,
		IndexerRecord loadWriter
	);

	Future<LoadStartContext> prepareStart(IndexerLoadRecord load);
}
