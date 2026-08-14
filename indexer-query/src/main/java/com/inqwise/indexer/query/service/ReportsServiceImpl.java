package com.inqwise.indexer.query.service;

import java.util.Objects;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.query.InvalidReportRequestException;
import com.inqwise.indexer.query.ReportExecutionContextResolver;
import com.inqwise.indexer.query.ReportNotFoundException;
import com.inqwise.indexer.query.ReportsFacade;
import com.inqwise.indexer.query.UnsupportedReportSchemaException;
import com.inqwise.indexer.query.monitoring.ReportExecutionOutcome;
import com.inqwise.indexer.query.monitoring.ReportOperationalMonitor;

import io.vertx.core.Future;

public final class ReportsServiceImpl implements ReportsService {
	private final ReportsFacade reports;
	private final ReportExecutionContextResolver contexts;
	private final ReportCaller caller;
	private final ReportOperationalMonitor monitor;

	public ReportsServiceImpl(
		ReportsFacade reports,
		ReportExecutionContextResolver contexts
	) {
		this(reports, contexts, null, ReportOperationalMonitor.NOOP);
	}

	public ReportsServiceImpl(
		ReportsFacade reports,
		ReportExecutionContextResolver contexts,
		ReportCaller caller
	) {
		this(reports, contexts, caller, ReportOperationalMonitor.NOOP);
	}

	public ReportsServiceImpl(
		ReportsFacade reports,
		ReportExecutionContextResolver contexts,
		ReportCaller caller,
		ReportOperationalMonitor monitor
	) {
		this.reports = Objects.requireNonNull(reports, "reports");
		this.contexts = Objects.requireNonNull(contexts, "contexts");
		this.caller = caller;
		this.monitor = Objects.requireNonNull(monitor, "monitor");
	}

	@Override
	public Future<ReportExecutionResult> execute(ReportExecutionRequest request) {
		String reportName = request == null ? null : request.getReportName();
		long startedAt = System.nanoTime();
		recordStarted(reportName);
		try {
			if (request == null) {
				throw new InvalidReportRequestException("Request is required");
			}
			if (request.getReportName() == null || request.getReportName().isBlank()) {
				throw new InvalidReportRequestException("Report name is required");
			}
			Future<ReportExecutionResult> execution = contexts.resolve(caller, request)
				.compose(context -> reports.execute(
					Objects.requireNonNull(context, "resolved context"),
					request
				));
			return execution.transform(result -> {
				recordCompleted(
					reportName,
					result.succeeded()
						? ReportExecutionOutcome.SUCCEEDED
						: outcome(result.cause()),
					startedAt
				);
				return result.succeeded()
					? Future.succeededFuture(result.result())
					: Future.failedFuture(normalize(result.cause()));
			});
		} catch (Throwable error) {
			recordCompleted(reportName, outcome(error), startedAt);
			return Future.failedFuture(normalize(error));
		}
	}

	private ReportExecutionOutcome outcome(Throwable error) {
		if (error instanceof InvalidReportRequestException
			|| error instanceof ReportNotFoundException
			|| error instanceof UnsupportedReportSchemaException) {
			return ReportExecutionOutcome.INVALID;
		}
		return ReportExecutionOutcome.FAILED;
	}

	private void recordStarted(String reportName) {
		try {
			monitor.executionStarted(reportName);
		} catch (RuntimeException ignored) {
			// Operational monitoring must not fail report execution.
		}
	}

	private void recordCompleted(
		String reportName,
		ReportExecutionOutcome outcome,
		long startedAt
	) {
		try {
			monitor.executionCompleted(
				reportName,
				outcome,
				Math.max(0L, System.nanoTime() - startedAt)
			);
		} catch (RuntimeException ignored) {
			// Operational monitoring must not fail report execution.
		}
	}

	private ErrorTicket normalize(Throwable error) {
		return QueryErrors.normalize(error);
	}
}
