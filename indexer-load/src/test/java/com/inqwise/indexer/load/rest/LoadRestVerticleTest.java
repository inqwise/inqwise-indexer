package com.inqwise.indexer.load.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.load.service.LoadServiceVerticle;
import com.inqwise.indexer.load.service.LoadServices;
import com.inqwise.indexer.load.testing.RecordingLoadManagementService;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoadRestVerticleTest {
	@Test
	void createsAndCancelsLoadThroughEventBusProxy(Vertx vertx, VertxTestContext testContext) {
		RecordingLoadManagementService domain = new RecordingLoadManagementService();
		String address = LoadServices.address("rest-test");
		LoadRestVerticle rest = new LoadRestVerticle(new LoadRestOptions()
			.setPort(0)
			.setServiceAddress(address));

		vertx.deployVerticle(new LoadServiceVerticle(domain, address))
			.compose(ignored -> vertx.deployVerticle(rest))
			.compose(ignored -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.POST,
				"/loads",
				new JsonObject()
					.put("provider_id", "archive")
					.put("target_id", 11),
				201
			))
			.compose(created -> {
				assertEquals(91, created.getJsonObject("load").getInteger("indexer_id"));
				return request(
					vertx,
					rest.actualPort(),
					HttpMethod.POST,
					"/loads/91/start?expected_version=4",
					null,
					200
				);
			})
			.compose(started -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.POST,
				"/loads/91/recover-created?expected_version=4",
				null,
				200
			))
			.compose(recovered -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.POST,
				"/loads/91/approve-publication?expected_version=4",
				new JsonObject()
					.put("approved_at", "2026-01-03T00:00:00Z")
					.put("approved_by", "operator")
					.put("approval_reason", "verified"),
				200
			))
			.compose(approved -> {
				return request(
					vertx,
					rest.actualPort(),
					HttpMethod.DELETE,
					"/loads/91?expected_version=4&reason=operator",
					null,
					202
				);
			})
			.compose(cancelled -> {
				assertEquals("ACCEPTED", cancelled.getString("status"));
				return request(
					vertx,
					rest.actualPort(),
					HttpMethod.POST,
					"/loads",
					new JsonObject().put("target_id", 11),
					400
				);
			})
			.onComplete(testContext.succeeding(invalid -> testContext.verify(() -> {
				assertEquals(400, invalid.getInteger("status"));
				assertEquals(4L, domain.started().expectedVersion());
				assertEquals(4L, domain.recovered().expectedVersion());
				assertEquals("operator", domain.approved().approvedBy());
				assertEquals("verified", domain.approved().approvalReason());
				assertEquals(91, domain.cancelled().indexerId());
				assertEquals(4L, domain.cancelled().expectedVersion());
				assertEquals("operator", domain.cancelled().reason());
				testContext.completeNow();
			})));
	}

	private io.vertx.core.Future<JsonObject> request(
		Vertx vertx,
		int port,
		HttpMethod method,
		String uri,
		JsonObject body,
		int expectedStatus
	) {
		return vertx.createHttpClient()
			.request(method, port, "127.0.0.1", uri)
			.compose(request -> body == null ? request.send() : request
				.putHeader("content-type", "application/json")
				.send(body.encode()))
			.compose(response -> {
				assertEquals(expectedStatus, response.statusCode());
				return response.body().map(buffer -> buffer.toJsonObject());
			});
	}
}
