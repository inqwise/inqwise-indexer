package com.inqwise.indexer.service.runtime;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

@ProxyGen
@VertxGen
public interface RuntimeService {
	Future<RuntimeStatusResult> status();

	Future<Void> reconcileIndexer(RuntimeReconcileRequest request);
}
