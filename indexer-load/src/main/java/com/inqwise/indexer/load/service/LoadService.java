package com.inqwise.indexer.load.service;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

@ProxyGen
@VertxGen
public interface LoadService {
	Future<LoadResult> create(LoadCreateRequest request);

	Future<LoadResult> start(LoadVersionRequest request);

	Future<LoadResult> recoverCreated(LoadVersionRequest request);

	Future<LoadResult> approvePublication(LoadApprovalRequest request);

	Future<Void> cancel(LoadCancelRequest request);
}
