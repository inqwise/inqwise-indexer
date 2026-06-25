package com.inqwise.indexer.providers;

import java.util.List;
import java.util.Optional;

import com.inqwise.indexer.IndexerType;

import io.vertx.core.Future;

public interface IndexerProvider {
	IndexerType type();

	Future<Optional<ResolvedIndexer>> getIndexerById(Integer indexerId);

	Future<List<ResolvedIndexer>> listIndexers(IndexerProviderQuery query);
}
