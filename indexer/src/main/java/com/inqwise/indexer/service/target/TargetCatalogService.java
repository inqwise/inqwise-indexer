package com.inqwise.indexer.service.target;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

@ProxyGen
@VertxGen
public interface TargetCatalogService {
	Future<TargetListResult> list(TargetQuery request);

	Future<TargetResult> get(TargetGetRequest request);

	Future<TargetResult> create(TargetCreateRequest request);

	Future<TargetResult> recoverProvisioning(TargetVersionRequest request);
}
