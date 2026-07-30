package com.inqwise.indexer.service.document;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

@ProxyGen
@VertxGen
public interface DocumentQueryService {
	Future<DocumentSearchResult> search(DocumentSearchRequest request);
}
