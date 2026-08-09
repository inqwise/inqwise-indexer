package com.inqwise.indexer.query;

import java.util.Objects;

import com.inqwise.indexer.query.service.ReportExecutionRequest;
import com.inqwise.indexer.query.service.ReportsService;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public final class TypedReportExecutor {
	private final ReportsService reports;

	public TypedReportExecutor(ReportsService reports) {
		this.reports = Objects.requireNonNull(reports, "reports");
	}

	public <Q, R> Future<R> execute(TypedReport<Q, R> report, Q request) {
		Objects.requireNonNull(report, "report");
		JsonObject parameters = Objects.requireNonNull(
			report.requestCodec().encode(Objects.requireNonNull(request, "request")),
			"encoded request"
		);
		ReportExecutionRequest execution = ReportExecutionRequest.builder()
			.withReportName(report.name())
			.withParameters(parameters)
			.build();
		return reports.execute(execution)
			.map(result -> report.resultCodec().decode(result.getPayload()));
	}
}
