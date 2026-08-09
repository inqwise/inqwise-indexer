package com.inqwise.indexer.query.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.query.ReportExecutionContextResolver;
import com.inqwise.indexer.query.ReportNotFoundException;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ReportsServiceImplTest {
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
}
