package com.inqwise.indexer.load.service;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

@ProxyGen
@VertxGen
public interface LoadQueryService {
	Future<LoadListResult> list(LoadListRequest request);
}
