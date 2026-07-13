package com.inqwise.indexer.load.repository;

import java.util.Optional;

import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

public interface LoadCleanupRepository {
	Future<Optional<IndexerRecord>> getIndexer(Integer indexerId);
}
