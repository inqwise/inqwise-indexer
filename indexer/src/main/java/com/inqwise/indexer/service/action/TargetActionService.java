package com.inqwise.indexer.service.action;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

@ProxyGen
@VertxGen
public interface TargetActionService {
	Future<TargetActionSubmitResult> submit(TargetActionSubmitRequest request);
}
