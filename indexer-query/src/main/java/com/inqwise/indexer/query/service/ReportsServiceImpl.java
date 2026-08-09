package com.inqwise.indexer.query.service;

import java.util.Objects;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.query.InvalidReportRequestException;
import com.inqwise.indexer.query.ReportExecutionContextResolver;
import com.inqwise.indexer.query.ReportsFacade;

import io.vertx.core.Future;

public final class ReportsServiceImpl implements ReportsService {
	private final ReportsFacade reports;
	private final ReportExecutionContextResolver contexts;
	private final ReportCaller caller;

	public ReportsServiceImpl(
		ReportsFacade reports,
		ReportExecutionContextResolver contexts
	) {
		this(reports, contexts, null);
	}

	public ReportsServiceImpl(
		ReportsFacade reports,
		ReportExecutionContextResolver contexts,
		ReportCaller caller
	) {
		this.reports = Objects.requireNonNull(reports, "reports");
		this.contexts = Objects.requireNonNull(contexts, "contexts");
		this.caller = caller;
	}

	@Override
	public Future<ReportExecutionResult> execute(ReportExecutionRequest request) {
		try {
			if (request == null) {
				throw new InvalidReportRequestException("Request is required");
			}
			if (request.getReportName() == null || request.getReportName().isBlank()) {
				throw new InvalidReportRequestException("Report name is required");
			}
			return contexts.resolve(caller, request)
				.compose(context -> reports.execute(
					Objects.requireNonNull(context, "resolved context"),
					request
				))
				.recover(error -> Future.failedFuture(normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(normalize(error));
		}
	}

	private ErrorTicket normalize(Throwable error) {
		return QueryErrors.normalize(error);
	}
}
