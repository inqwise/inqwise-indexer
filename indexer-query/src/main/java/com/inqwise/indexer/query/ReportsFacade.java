package com.inqwise.indexer.query;

import com.inqwise.indexer.query.service.ReportExecutionRequest;
import com.inqwise.indexer.query.service.ReportExecutionResult;

import io.vertx.core.Future;

public interface ReportsFacade {
	Future<ReportExecutionResult> execute(
		ReportExecutionContext context,
		ReportExecutionRequest request
	);
}
