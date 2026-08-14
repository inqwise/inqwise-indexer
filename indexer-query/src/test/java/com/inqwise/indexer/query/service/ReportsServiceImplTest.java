package com.inqwise.indexer.query.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.query.ReportExecutionContextResolver;
import com.inqwise.indexer.query.ReportNotFoundException;
import com.inqwise.indexer.query.monitoring.ReportExecutionOutcome;
import com.inqwise.indexer.query.monitoring.ReportOperationalMonitor;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ReportsServiceImplTest {
	@Test
	void monitorsSuccessfulExecution(VertxTestContext testContext) {
		RecordingMonitor monitor = new RecordingMonitor();
		ReportsService service = new ReportsServiceImpl(
			(context, request) -> Future.succeededFuture(
				ReportExecutionResult.builder().withPayload(new JsonObject()).build()
			),
			ReportExecutionContextResolver.UNBOUNDED,
			null,
			monitor
		);

		service.execute(ReportExecutionRequest.builder()
			.withReportName("stories")
			.build()).onComplete(testContext.succeeding(result -> testContext.verify(() -> {
			assertEquals("stories", monitor.startedReport);
			assertEquals("stories", monitor.completedReport);
			assertEquals(ReportExecutionOutcome.SUCCEEDED, monitor.outcome);
			assertEquals(1, monitor.started);
			assertEquals(1, monitor.completed);
			testContext.completeNow();
		})));
	}

	@Test
	void classifiesInvalidRequestForMonitoring(VertxTestContext testContext) {
		RecordingMonitor monitor = new RecordingMonitor();
		ReportsService service = new ReportsServiceImpl(
			(context, request) -> Future.failedFuture("not reached"),
			ReportExecutionContextResolver.UNBOUNDED,
			null,
			monitor
		);

		service.execute(null).onComplete(testContext.failing(error -> testContext.verify(() -> {
			assertEquals(ReportExecutionOutcome.INVALID, monitor.outcome);
			assertEquals(1, monitor.started);
			assertEquals(1, monitor.completed);
			testContext.completeNow();
		})));
	}
	@Test
	void normalizesMissingReportAtServiceBoundary(VertxTestContext testContext) {
		ReportsService service = new ReportsServiceImpl(
			(context, request) -> Future.failedFuture(
				new ReportNotFoundException(request.getReportName())
			),
			ReportExecutionContextResolver.UNBOUNDED
		);

		service.execute(ReportExecutionRequest.builder()
			.withReportName("missing")
			.build()).onComplete(testContext.failing(error -> testContext.verify(() -> {
			ErrorTicket ticket = assertInstanceOf(ErrorTicket.class, error);
			assertEquals(QueryErrorCodes.ReportNotFound, ticket.getError());
			testContext.completeNow();
		})));
	}

	@Test
	void keepsUnexpectedProviderErrorsInternal(VertxTestContext testContext) {
		ReportsService service = new ReportsServiceImpl(
			(context, request) -> Future.failedFuture(
				new IllegalArgumentException("malformed stored document")
			),
			ReportExecutionContextResolver.UNBOUNDED
		);

		service.execute(ReportExecutionRequest.builder()
			.withReportName("stories")
			.build()).onComplete(testContext.failing(error -> testContext.verify(() -> {
			ErrorTicket ticket = assertInstanceOf(ErrorTicket.class, error);
			assertEquals(QueryErrorCodes.InternalError, ticket.getError());
			testContext.completeNow();
		})));
	}

	private static final class RecordingMonitor implements ReportOperationalMonitor {
		private int started;
		private int completed;
		private String startedReport;
		private String completedReport;
		private ReportExecutionOutcome outcome;

		@Override
		public void executionStarted(String reportName) {
			started++;
			startedReport = reportName;
		}

		@Override
		public void executionCompleted(
			String reportName,
			ReportExecutionOutcome outcome,
			long durationNanos
		) {
			completed++;
			completedReport = reportName;
			this.outcome = outcome;
		}
	}
}
