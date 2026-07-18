package com.inqwise.indexer.service.indexer;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

@ProxyGen
@VertxGen
public interface IndexerCatalogService {
	Future<IndexerListResult> list(IndexerQuery request);

	Future<IndexerResult> get(IndexerGetRequest request);

	Future<IndexerResult> activate(IndexerVersionRequest request);

	Future<IndexerResult> deactivate(IndexerVersionRequest request);
}
