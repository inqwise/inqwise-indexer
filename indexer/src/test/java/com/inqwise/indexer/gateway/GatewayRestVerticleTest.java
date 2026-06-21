package com.inqwise.indexer.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
	void proxiesAdminIndexersUsingDeployedGatewayInstance(Vertx vertx, VertxTestContext testContext) {
		HttpServer upstream = vertx.createHttpServer()
			.requestHandler(request -> {
				assertEquals(HttpMethod.GET, request.method());
				assertEquals("/admin/indexers?target_id=7&runtime_state=ACTIVE", request.uri());
				request.response()
					.putHeader("content-type", "application/json")
					.end(new JsonObject()
						.put("indexers", new JsonArray()
							.add(new JsonObject().put("id", 11)))
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
					"/gateway/admin/indexers?target_id=7&runtime_state=ACTIVE"
				)
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					assertEquals("application/json", response.getHeader("content-type"));
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject json = body.toJsonObject();
				assertEquals(11, json.getJsonArray("indexers").getJsonObject(0).getInteger("id"));
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
				JsonObject error = body.toJsonObject();
				assertEquals(GatewayErrorCodes.AdminRestNotConfigured.name(), error.getString("code"));
				assertEquals(GatewayErrorCodes.GROUP, error.getString("group"));
				assertEquals(503, error.getInteger("status"));
				assertEquals("Gateway upstream is not configured", error.getString("detail"));
				testContext.completeNow();
			})));
	}

	@Test
	void returnsSafeUnavailableErrorWhenAdminRestCannotBeReached(Vertx vertx, VertxTestContext testContext) {
		GatewayRestVerticle gateway = new GatewayRestVerticle(new GatewayRestOptions()
			.setPort(0)
			.setAdminRestBaseUri("http://127.0.0.1:1"));

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/admin/targets")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(502, response.statusCode());
					return response.body();
			}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject error = body.toJsonObject();
				assertEquals(GatewayErrorCodes.UpstreamUnavailable.name(), error.getString("code"));
				assertEquals(GatewayErrorCodes.GROUP, error.getString("group"));
				assertEquals(502, error.getInteger("status"));
				assertEquals("Upstream service unavailable", error.getString("detail"));
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsGatewayRequestWhenConfiguredApiKeyIsMissing(Vertx vertx, VertxTestContext testContext) {
		GatewayRestVerticle gateway = new GatewayRestVerticle(new GatewayRestOptions()
			.setPort(0)
			.setApiKey("secret"));

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(401, response.statusCode());
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject error = body.toJsonObject();
				assertEquals(GatewayErrorCodes.Unauthenticated.name(), error.getString("code"));
				assertEquals(GatewayErrorCodes.GROUP, error.getString("group"));
				assertEquals(401, error.getInteger("status"));
				testContext.completeNow();
			})));
	}

	@Test
	void acceptsGatewayRequestWhenConfiguredApiKeyMatches(Vertx vertx, VertxTestContext testContext) {
		GatewayRestVerticle gateway = new GatewayRestVerticle(new GatewayRestOptions()
			.setPort(0)
			.setApiKey("secret")
			.setApiKeyHeader("x-indexer-key"));

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
				.compose(request -> request
					.putHeader("x-indexer-key", "secret")
					.send())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject status = body.toJsonObject();
				assertEquals("UP", status.getString("status"));
				testContext.completeNow();
			})));
	}

	@Test
	void rateLimitsGatewayRequestWhenConfiguredLimitIsExceeded(Vertx vertx, VertxTestContext testContext) {
		GatewayRestVerticle gateway = new GatewayRestVerticle(new GatewayRestOptions()
			.setPort(0)
			.setRateLimitRequests(1)
			.setRateLimitWindowMs(60000L));

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
				.compose(request -> request.send()))
			.compose(response -> {
				assertEquals(200, response.statusCode());
				return vertx.createHttpClient()
					.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
					.compose(request -> request.send());
			})
			.compose(response -> {
				assertEquals(429, response.statusCode());
				return response.body();
			})
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject error = body.toJsonObject();
				assertEquals(GatewayErrorCodes.RateLimited.name(), error.getString("code"));
				assertEquals(GatewayErrorCodes.GROUP, error.getString("group"));
				assertEquals(429, error.getInteger("status"));
				testContext.completeNow();
			})));
	}

	@Test
	void doesNotExposeConfiguredApiKeyInOptionsJson() {
		JsonObject json = new GatewayRestOptions()
			.setApiKey("secret")
			.setApiKeyHeader("x-indexer-key")
			.setRateLimitRequests(10)
			.setRateLimitWindowMs(1000L)
			.toJson();

		assertFalse(json.containsKey(GatewayRestOptions.Keys.API_KEY));
		assertEquals("x-indexer-key", json.getString(GatewayRestOptions.Keys.API_KEY_HEADER));
		assertEquals(10, json.getInteger(GatewayRestOptions.Keys.RATE_LIMIT_REQUESTS));
		assertEquals(1000L, json.getLong(GatewayRestOptions.Keys.RATE_LIMIT_WINDOW_MS));
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
	void appliesGatewayHooksAroundAdminIndexerProxyOperation(Vertx vertx, VertxTestContext testContext) {
		List<String> calls = new ArrayList<>();
		HttpServer upstream = vertx.createHttpServer()
			.requestHandler(request -> request.response()
				.putHeader("content-type", "application/json")
				.end(new JsonObject().put("indexers", new JsonArray()).encode()));
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

		upstream.listen(0, "127.0.0.1")
			.compose(server -> {
				GatewayRestOptions options = new GatewayRestOptions()
					.setPort(0)
					.setAdminRestBaseUri("http://127.0.0.1:" + server.actualPort());
				GatewayRestVerticle gateway = new GatewayRestVerticle(options, hooks);
				return vertx.deployVerticle(gateway)
					.map(gateway);
			})
			.compose(gateway -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/admin/indexers")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				assertEquals(List.of(
					"authenticate:gatewayListIndexers",
					"authorize:gatewayListIndexers",
					"rateLimit:gatewayListIndexers",
					"auditSuccess:gatewayListIndexers"
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
				JsonObject error = body.toJsonObject();
				assertEquals(GatewayErrorCodes.GatewayRequestRejected.name(), error.getString("code"));
				assertEquals(GatewayErrorCodes.GROUP, error.getString("group"));
				assertEquals(403, error.getInteger("status"));
				assertEquals("Gateway request rejected", error.getString("detail"));
				assertEquals(1, auditFailures.get());
				testContext.completeNow();
			})));
	}

	@Test
	void mapsTypedGatewayHookFailuresToPublicErrorContract(Vertx vertx, VertxTestContext testContext) {
		GatewayRequestHooks hooks = new GatewayRequestHooks() {
			@Override
			public Future<Void> authenticate(RoutingContext context, String operationId) {
				return Future.failedFuture(GatewayErrorResponses.unauthenticated());
			}
		};
		GatewayRestVerticle gateway = new GatewayRestVerticle(new GatewayRestOptions().setPort(0), hooks);

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(401, response.statusCode());
					return response.body();
			}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject error = body.toJsonObject();
				assertEquals(GatewayErrorCodes.Unauthenticated.name(), error.getString("code"));
				assertEquals(GatewayErrorCodes.GROUP, error.getString("group"));
				assertEquals(401, error.getInteger("status"));
				assertEquals("Authentication is required", error.getString("detail"));
				testContext.completeNow();
			})));
	}
}
