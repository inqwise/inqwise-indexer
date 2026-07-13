package com.inqwise.indexer.load.repository;

import java.util.Optional;

import io.vertx.core.Future;

public interface LoadCleanupRepository {
	Future<Optional<LoadIndexerReference>> getIndexer(Integer indexerId);
}
