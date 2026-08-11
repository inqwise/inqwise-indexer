package com.inqwise.indexer.query.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.query.presentation.ReportPresentation;
import com.inqwise.indexer.query.service.ReportDiscoveryResult;
import com.inqwise.indexer.query.service.ReportDiscoveryServiceVerticle;
import com.inqwise.indexer.query.service.ReportDiscoveryServices;
import com.inqwise.indexer.query.service.ReportExecutionRequest;
import com.inqwise.indexer.query.service.ReportExecutionResult;
import com.inqwise.indexer.query.service.ReportsServiceVerticle;
import com.inqwise.indexer.query.service.ReportsServices;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ReportsRestVerticleTest {
	@Test
	void discoversAndExecutesWithoutConsumerContracts(
		Vertx vertx,
		VertxTestContext testContext
	) {
		String reportsAddress = ReportsServices.address("rest-test");
		String discoveryAddress = ReportDiscoveryServices.address("rest-test");
		AtomicReference<ReportExecutionRequest> captured = new AtomicReference<>();
		ReportPresentation presentation = ReportPresentation.builder()
			.withName("consumer.summary")
			.withTitle("Summary")
			.withDescription("A neutral report.")
			.withParametersSchema(objectSchema())
			.withResultSchema(objectSchema())
			.build();
		ReportsRestVerticle rest = new ReportsRestVerticle(ReportsRestOptions.builder()
			.withEnabled(true)
			.withPort(0)
			.withReportsAddress(reportsAddress)
			.withDiscoveryAddress(discoveryAddress)
			.build());

		vertx.deployVerticle(new ReportDiscoveryServiceVerticle(
			() -> Future.succeededFuture(ReportDiscoveryResult.builder()
				.withReports(List.of(presentation))
				.build()),
			discoveryAddress
		)).compose(ignored -> vertx.deployVerticle(new ReportsServiceVerticle(
			request -> {
				captured.set(request);
				return Future.succeededFuture(ReportExecutionResult.builder()
					.withPayload(new JsonObject().put(
						"rows",
						new io.vertx.core.json.JsonArray().add(
							new JsonObject().put("value", 7)
						)
					))
					.build());
			},
			reportsAddress
		))).compose(ignored -> vertx.deployVerticle(rest))
			.compose(ignored -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.GET,
				"/reports",
				null
			))
			.compose(response -> response.body().map(body -> {
				assertEquals(200, response.statusCode());
				JsonObject report = body.toJsonObject()
					.getJsonArray("reports")
					.getJsonObject(0);
				assertEquals("consumer.summary", report.getString("name"));
				assertEquals("Summary", report.getString("title"));
				return null;
			}))
			.compose(ignored -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.POST,
				"/reports/consumer.summary/executions",
				new JsonObject().put("limit", 10)
			))
			.compose(response -> response.body().map(body -> {
					assertEquals(200, response.statusCode());
					assertEquals(7, body.toJsonObject()
						.getJsonArray("rows")
						.getJsonObject(0)
						.getInteger("value"));
					assertEquals("consumer.summary", captured.get().getReportName());
					assertEquals(10, captured.get().getParameters().getInteger("limit"));
					return null;
				}))
			.compose(ignored -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.POST,
				"/reports/hidden.report/executions",
				new JsonObject()
			))
			.onComplete(testContext.succeeding(response -> response.body()
				.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
					assertEquals(404, response.statusCode());
					assertEquals("ReportNotFound", body.toJsonObject().getString("code"));
					testContext.completeNow();
				})))
			));
	}

	private static Future<HttpClientResponse> request(
		Vertx vertx,
		int port,
		HttpMethod method,
		String uri,
		JsonObject body
	) {
		return vertx.createHttpClient()
			.request(method, port, "127.0.0.1", uri)
			.compose(request -> {
				if (body == null) {
					return request.send();
				}
				return request
					.putHeader("content-type", "application/json")
					.send(body.encode());
			});
	}

	private static JsonObject objectSchema() {
		return new JsonObject()
			.put("$schema", ReportPresentation.JSON_SCHEMA_DIALECT)
			.put("type", "object");
	}
}
