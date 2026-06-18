package com.inqwise.indexer.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class GatewayRestVerticleTest {
	@Test
	void proxiesAdminTargetsUsingDeployedGatewayInstance(Vertx vertx, VertxTestContext testContext) {
		HttpServer upstream = vertx.createHttpServer()
			.requestHandler(request -> {
				assertEquals(HttpMethod.GET, request.method());
				assertEquals("/admin/targets?target_name=customers", request.uri());
				request.response()
					.putHeader("content-type", "application/json")
					.end(new JsonObject()
						.put("targets", new JsonArray()
							.add(new JsonObject().put("target_name", "customers")))
						.encode());
			});

		upstream.listen(0, "127.0.0.1")
			.compose(server -> {
				GatewayRestOptions options = new GatewayRestOptions()
					.setPort(0)
					.setAdminRestBaseUri("http://127.0.0.1:" + server.actualPort());
				GatewayRestVerticle configuredGateway = new GatewayRestVerticle(options);
				return vertx.deployVerticle(configuredGateway)
					.map(configuredGateway);
			})
			.compose(deployedGateway -> vertx.createHttpClient()
				.request(
					HttpMethod.GET,
					deployedGateway.actualPort(),
					"127.0.0.1",
					"/gateway/admin/targets?target_name=customers"
				)
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					assertEquals("application/json", response.getHeader("content-type"));
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject json = body.toJsonObject();
				assertEquals("customers", json.getJsonArray("targets").getJsonObject(0).getString("target_name"));
				testContext.completeNow();
			})));
	}

	@Test
	void returnsUnavailableWhenAdminRestIsNotConfigured(Vertx vertx, VertxTestContext testContext) {
		GatewayRestVerticle gateway = new GatewayRestVerticle(new GatewayRestOptions().setPort(0));

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/admin/targets")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(503, response.statusCode());
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject error = body.toJsonObject().getJsonObject("error");
				assertEquals("ADMIN_REST_NOT_CONFIGURED", error.getString("code"));
				testContext.completeNow();
			})));
	}

	@Test
	void appliesGatewayHooksAroundSuccessfulOperation(Vertx vertx, VertxTestContext testContext) {
		List<String> calls = new ArrayList<>();
		GatewayRequestHooks hooks = new GatewayRequestHooks() {
			@Override
			public Future<Void> authenticate(RoutingContext context, String operationId) {
				calls.add("authenticate:" + operationId);
				return Future.succeededFuture();
			}

			@Override
			public Future<Void> authorize(RoutingContext context, String operationId) {
				calls.add("authorize:" + operationId);
				return Future.succeededFuture();
			}

			@Override
			public Future<Void> rateLimit(RoutingContext context, String operationId) {
				calls.add("rateLimit:" + operationId);
				return Future.succeededFuture();
			}

			@Override
			public void auditSuccess(RoutingContext context, String operationId) {
				calls.add("auditSuccess:" + operationId);
			}
		};
		GatewayRestVerticle gateway = new GatewayRestVerticle(new GatewayRestOptions().setPort(0), hooks);

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				assertEquals(List.of(
					"authenticate:gatewayStatus",
					"authorize:gatewayStatus",
					"rateLimit:gatewayStatus",
					"auditSuccess:gatewayStatus"
				), calls);
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsGatewayOperationWhenHookFails(Vertx vertx, VertxTestContext testContext) {
		AtomicInteger auditFailures = new AtomicInteger();
		GatewayRequestHooks hooks = new GatewayRequestHooks() {
			@Override
			public Future<Void> authorize(RoutingContext context, String operationId) {
				return Future.failedFuture("denied");
			}

			@Override
			public void auditFailure(RoutingContext context, String operationId, Throwable error) {
				auditFailures.incrementAndGet();
			}
		};
		GatewayRestVerticle gateway = new GatewayRestVerticle(new GatewayRestOptions().setPort(0), hooks);

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(403, response.statusCode());
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject error = body.toJsonObject().getJsonObject("error");
				assertEquals("GATEWAY_REQUEST_REJECTED", error.getString("code"));
				assertEquals(1, auditFailures.get());
				testContext.completeNow();
			})));
	}
}
