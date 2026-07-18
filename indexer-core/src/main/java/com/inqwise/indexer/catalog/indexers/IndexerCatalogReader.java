package com.inqwise.indexer.catalog.indexers;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;

public interface IndexerCatalogReader {
	Future<List<IndexerCatalogEntry>> list(IndexerCatalogQuery query);

	Future<Optional<IndexerCatalogEntry>> findById(Integer id);

	Future<Optional<IndexerCatalogEntry>> findByUid(String uid);
}
