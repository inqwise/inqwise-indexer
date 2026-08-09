package com.inqwise.indexer.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.query.service.ReportCaller;
import com.inqwise.indexer.query.service.ReportExecutionRequest;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ConsumerReportExecutionContextResolverTest {
	@Test
	void resolvesOnlyTheTrustedConsumerScope(VertxTestContext testContext) {
		ReportExecutionContext expected = ReportExecutionContext.builder()
			.withScope(ReportQueryScope.builder()
				.withFromInclusive(Instant.parse("2026-01-01T00:00:00Z"))
				.withToExclusive(Instant.parse("2026-02-01T00:00:00Z"))
				.withMaxLimit(7)
				.build())
			.withTrustedAttributes(Map.of("tier", "bounded"))
			.build();
		ConsumerReportExecutionContextResolver resolver =
			new ConsumerReportExecutionContextResolver(Map.of("consumer-a", expected));

		resolver.resolve(caller("consumer-a"), request())
			.onComplete(testContext.succeeding(actual -> testContext.verify(() -> {
				assertEquals(expected, actual);
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsUnknownConsumers(VertxTestContext testContext) {
		ConsumerReportExecutionContextResolver resolver =
			new ConsumerReportExecutionContextResolver(Map.of(
				"consumer-a",
				ReportExecutionContext.builder().build()
			));

		resolver.resolve(caller("consumer-b"), request())
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertInstanceOf(InvalidReportRequestException.class, error);
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsMissingCaller(VertxTestContext testContext) {
		ConsumerReportExecutionContextResolver resolver =
			new ConsumerReportExecutionContextResolver(Map.of(
				"consumer-a",
				ReportExecutionContext.builder().build()
			));

		resolver.resolve(null, request())
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertInstanceOf(InvalidReportRequestException.class, error);
				testContext.completeNow();
			})));
	}

	private ReportCaller caller(String consumerName) {
		return ReportCaller.builder()
			.withConsumerName(consumerName)
			.withSubject("user-1")
			.build();
	}

	private ReportExecutionRequest request() {
		return ReportExecutionRequest.builder()
			.withReportName("example")
			.build();
	}
}
