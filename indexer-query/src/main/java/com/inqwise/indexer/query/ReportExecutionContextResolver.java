package com.inqwise.indexer.query;

import com.inqwise.indexer.query.service.ReportExecutionRequest;
import com.inqwise.indexer.query.service.ReportCaller;

import io.vertx.core.Future;

public interface ReportExecutionContextResolver {
	ReportExecutionContextResolver UNBOUNDED = (caller, request) -> Future.succeededFuture(
		ReportExecutionContext.builder().build()
	);

	Future<ReportExecutionContext> resolve(
		ReportCaller caller,
		ReportExecutionRequest request
	);
}
