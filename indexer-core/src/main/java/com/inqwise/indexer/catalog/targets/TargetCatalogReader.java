package com.inqwise.indexer.catalog.targets;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;

public interface TargetCatalogReader {
	Future<List<TargetCatalogEntry>> list(TargetCatalogQuery query);

	Future<Optional<TargetCatalogEntry>> findById(Integer id);

	Future<Optional<TargetCatalogEntry>> findByUid(String uid);
}
