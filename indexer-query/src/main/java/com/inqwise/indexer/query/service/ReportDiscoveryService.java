package com.inqwise.indexer.query.service;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

@ProxyGen
@VertxGen
public interface ReportDiscoveryService {
	Future<ReportDiscoveryResult> discover();
}
