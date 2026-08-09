package com.inqwise.indexer.query.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.query.DefaultTypedReport;
import com.inqwise.indexer.query.ReportExecutionContext;
import com.inqwise.indexer.query.ReportRequestCodec;
import com.inqwise.indexer.query.ReportResultCodec;
import com.inqwise.indexer.query.TypedReportExecutor;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ReportsServiceVerticleTest {
	@Test
	void executesThroughGeneratedProxyAndTypedHelper(
		Vertx vertx,
		VertxTestContext testContext
	) {
		AtomicBoolean contextResolved = new AtomicBoolean();
		AtomicReference<ReportCaller> resolvedCaller = new AtomicReference<>();
		ReportsServiceVerticle verticle = new ReportsServiceVerticle(
			(context, request) -> Future.succeededFuture(ReportExecutionResult.builder()
				.withPayload(new JsonObject()
					.put("value", request.getParameters().getString("value"))
					.put("trusted", context.trustedAttributes().get("scope")))
				.build()),
			(caller, request) -> {
				contextResolved.set(true);
				resolvedCaller.set(caller);
				return Future.succeededFuture(ReportExecutionContext.builder()
					.withTrustedAttributes(java.util.Map.of("scope", "trusted"))
					.build());
			},
			ReportCaller.builder()
				.withConsumerName("consumer-a")
				.withSubject("user-42")
				.build(),
			ReportsServices.DEFAULT_ADDRESS
		);

		vertx.deployVerticle(verticle)
			.compose(deploymentId -> {
				ReportsService proxy = ReportsServices.proxy(vertx);
				TypedReportExecutor typed = new TypedReportExecutor(proxy);
				return typed.execute(DefaultTypedReport
					.<TestRequest, TestResult>builder()
					.withName("typed")
					.withRequestCodec(requestCodec())
					.withResultCodec(resultCodec())
					.build(), new TestRequest("hello"))
					.compose(result -> vertx.undeploy(deploymentId).map(result));
			})
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("hello", result.value());
				assertEquals("trusted", result.trusted());
				assertTrue(contextResolved.get());
				assertEquals("consumer-a", resolvedCaller.get().getConsumerName());
				assertEquals("user-42", resolvedCaller.get().getSubject());
				testContext.completeNow();
			})));
	}

	private ReportRequestCodec<TestRequest> requestCodec() {
		return new ReportRequestCodec<>() {
			@Override
			public TestRequest decode(JsonObject parameters) {
				return new TestRequest(parameters.getString("value"));
			}

			@Override
			public JsonObject encode(TestRequest request) {
				return new JsonObject().put("value", request.value());
			}
		};
	}

	private ReportResultCodec<TestResult> resultCodec() {
		return new ReportResultCodec<>() {
			@Override
			public TestResult decode(JsonObject payload) {
				return new TestResult(
					payload.getString("value"),
					payload.getString("trusted")
				);
			}

			@Override
			public JsonObject encode(TestResult result) {
				return new JsonObject()
					.put("value", result.value())
					.put("trusted", result.trusted());
			}
		};
	}

	private record TestRequest(String value) {
	}

	private record TestResult(String value, String trusted) {
	}
}
