package com.inqwise.indexer.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientResponse;
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
	void doesNotExposeUpstreamAdminConflictDetails(Vertx vertx, VertxTestContext testContext) {
		HttpServer upstream = vertx.createHttpServer()
			.requestHandler(request -> request.response()
				.setStatusCode(409)
				.putHeader("content-type", "application/json")
				.end(new JsonObject()
					.put("code", "VersionConflict")
					.put("detail", "index private-customers-v3 queue customers-write has storage version 91")
					.encode()));

		upstream.listen(0, "127.0.0.1")
			.compose(server -> {
				GatewayRestOptions options = new GatewayRestOptions()
					.setPort(0)
					.setAdminRestBaseUri("http://127.0.0.1:" + server.actualPort());
				GatewayRestVerticle gateway = new GatewayRestVerticle(options);
				return vertx.deployVerticle(gateway).map(gateway);
			})
			.compose(gateway -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/admin/indexers")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(409, response.statusCode());
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject error = body.toJsonObject();
				assertEquals(GatewayErrorCodes.Conflict.name(), error.getString("code"));
				assertEquals(GatewayErrorCodes.GROUP, error.getString("group"));
				assertEquals(409, error.getInteger("status"));
				assertEquals("Request conflicts with current state", error.getString("detail"));
				assertFalse(body.toString().contains("private-customers-v3"));
				assertFalse(body.toString().contains("customers-write"));
				testContext.completeNow();
			})));
	}

	@Test
	void mapsUpstreamServerErrorToSafeUnavailableResponse(Vertx vertx, VertxTestContext testContext) {
		HttpServer upstream = vertx.createHttpServer()
			.requestHandler(request -> request.response()
				.setStatusCode(500)
				.end("storage node document-index-17 failed"));

		upstream.listen(0, "127.0.0.1")
			.compose(server -> {
				GatewayRestOptions options = new GatewayRestOptions()
					.setPort(0)
					.setAdminRestBaseUri("http://127.0.0.1:" + server.actualPort());
				GatewayRestVerticle gateway = new GatewayRestVerticle(options);
				return vertx.deployVerticle(gateway).map(gateway);
			})
			.compose(gateway -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/admin/targets")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(502, response.statusCode());
					return response.body();
				}))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject error = body.toJsonObject();
				assertEquals(GatewayErrorCodes.UpstreamUnavailable.name(), error.getString("code"));
				assertEquals(502, error.getInteger("status"));
				assertEquals("Upstream service unavailable", error.getString("detail"));
				assertFalse(body.toString().contains("document-index-17"));
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
	void propagatesBuiltInIdentityAndRequestMetadataToAudit(
		Vertx vertx,
		VertxTestContext testContext
	) {
		GatewayRestOptions options = new GatewayRestOptions()
			.setPort(0)
			.setApiKey("secret");
		Promise<GatewayAuditEvent> audited = Promise.promise();
		GatewayRequestHooks hooks = new GatewayBuiltInRequestHooks(options) {
			@Override
			public Future<Void> authorize(
				GatewayRequestMetadata request,
				GatewayPrincipal principal
			) {
				assertEquals("configured-api-key", principal.subject());
				assertEquals("api-key", principal.authenticationScheme());
				assertTrue(principal.authenticated());
				return Future.succeededFuture();
			}

			@Override
			public Future<Void> audit(GatewayAuditEvent event) {
				audited.complete(event);
				return Future.succeededFuture();
			}
		};
		GatewayRestVerticle gateway = new GatewayRestVerticle(options, hooks);
		AtomicReference<String> requestId = new AtomicReference<>();

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
				.compose(request -> request
					.putHeader("x-api-key", "secret")
					.send()))
			.compose(response -> {
				assertEquals(200, response.statusCode());
				requestId.set(response.getHeader("x-request-id"));
				return audited.future();
			})
			.onComplete(testContext.succeeding(event -> testContext.verify(() -> {
				assertEquals(requestId.get(), event.request().requestId());
				assertEquals("gatewayStatus", event.request().operationId());
				assertEquals("GET", event.request().method());
				assertEquals("/gateway/status", event.request().path());
				assertEquals(GatewayAuditOutcome.SUCCESS, event.outcome());
				assertEquals("configured-api-key", event.principal().orElseThrow().subject());
				assertTrue(event.failureCode().isEmpty());
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
			public Future<GatewayPrincipal> authenticate(
				RoutingContext context,
				GatewayRequestMetadata request
			) {
				calls.add("authenticate:" + request.operationId());
				return Future.succeededFuture(authenticatedPrincipal());
			}

			@Override
			public Future<Void> authorize(
				GatewayRequestMetadata request,
				GatewayPrincipal principal
			) {
				calls.add("authorize:" + request.operationId() + ':' + principal.subject());
				return Future.succeededFuture();
			}

			@Override
			public Future<Void> rateLimit(
				GatewayRequestMetadata request,
				GatewayPrincipal principal
			) {
				calls.add("rateLimit:" + request.operationId());
				return Future.succeededFuture();
			}

			@Override
			public Future<Void> audit(GatewayAuditEvent event) {
				calls.add("audit:" + event.request().operationId() + ':' + event.outcome());
				return Future.succeededFuture();
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
					"authorize:gatewayStatus:operator-17",
					"rateLimit:gatewayStatus",
					"audit:gatewayStatus:SUCCESS"
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
			public Future<GatewayPrincipal> authenticate(
				RoutingContext context,
				GatewayRequestMetadata request
			) {
				calls.add("authenticate:" + request.operationId());
				return Future.succeededFuture(authenticatedPrincipal());
			}

			@Override
			public Future<Void> authorize(
				GatewayRequestMetadata request,
				GatewayPrincipal principal
			) {
				calls.add("authorize:" + request.operationId() + ':' + principal.subject());
				return Future.succeededFuture();
			}

			@Override
			public Future<Void> rateLimit(
				GatewayRequestMetadata request,
				GatewayPrincipal principal
			) {
				calls.add("rateLimit:" + request.operationId());
				return Future.succeededFuture();
			}

			@Override
			public Future<Void> audit(GatewayAuditEvent event) {
				calls.add("audit:" + event.request().operationId() + ':' + event.outcome());
				return Future.succeededFuture();
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
					"authorize:gatewayListIndexers:operator-17",
					"rateLimit:gatewayListIndexers",
					"audit:gatewayListIndexers:SUCCESS"
				), calls);
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsGatewayOperationWhenHookFails(Vertx vertx, VertxTestContext testContext) {
		AtomicInteger auditFailures = new AtomicInteger();
		GatewayRequestHooks hooks = new GatewayRequestHooks() {
			@Override
			public Future<Void> authorize(
				GatewayRequestMetadata request,
				GatewayPrincipal principal
			) {
				return Future.failedFuture("denied");
			}

			@Override
			public Future<Void> audit(GatewayAuditEvent event) {
				if (event.outcome() == GatewayAuditOutcome.FAILURE) {
					auditFailures.incrementAndGet();
				}
				return Future.succeededFuture();
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
	void completesFailureAuditBeforeWritingRejection(
		Vertx vertx,
		VertxTestContext testContext
	) {
		Promise<GatewayAuditEvent> auditStarted = Promise.promise();
		Promise<Void> releaseAudit = Promise.promise();
		GatewayRequestHooks hooks = new GatewayRequestHooks() {
			@Override
			public Future<Void> authorize(
				GatewayRequestMetadata request,
				GatewayPrincipal principal
			) {
				return Future.failedFuture(GatewayErrorResponses.forbidden());
			}

			@Override
			public Future<Void> audit(GatewayAuditEvent event) {
				auditStarted.complete(event);
				return releaseAudit.future();
			}
		};
		GatewayRestVerticle gateway = new GatewayRestVerticle(
			new GatewayRestOptions().setPort(0),
			hooks
		);

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status"))
			.compose(request -> {
				Future<HttpClientResponse> response = request.send();
				return auditStarted.future()
					.compose(event -> {
						assertFalse(response.isComplete());
						assertEquals(GatewayAuditOutcome.FAILURE, event.outcome());
						assertEquals(
							GatewayErrorCodes.Forbidden.name(),
							event.failureCode().orElseThrow()
						);
						releaseAudit.complete();
						return response;
					});
			})
			.onComplete(testContext.succeeding(response -> testContext.verify(() -> {
				assertEquals(403, response.statusCode());
				testContext.completeNow();
			})));
	}

	@Test
	void mapsTypedGatewayHookFailuresToPublicErrorContract(Vertx vertx, VertxTestContext testContext) {
		GatewayRequestHooks hooks = new GatewayRequestHooks() {
			@Override
			public Future<GatewayPrincipal> authenticate(
				RoutingContext context,
				GatewayRequestMetadata request
			) {
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

	@Test
	void rejectsNullAuthenticatedPrincipal(Vertx vertx, VertxTestContext testContext) {
		AtomicReference<GatewayAuditEvent> audited = new AtomicReference<>();
		GatewayRequestHooks hooks = new GatewayRequestHooks() {
			@Override
			public Future<GatewayPrincipal> authenticate(
				RoutingContext context,
				GatewayRequestMetadata request
			) {
				return Future.succeededFuture();
			}

			@Override
			public Future<Void> audit(GatewayAuditEvent event) {
				audited.set(event);
				return Future.succeededFuture();
			}
		};
		GatewayRestVerticle gateway = new GatewayRestVerticle(
			new GatewayRestOptions().setPort(0),
			hooks
		);

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
				.compose(request -> request.send()))
			.onComplete(testContext.succeeding(response -> testContext.verify(() -> {
				assertEquals(403, response.statusCode());
				assertEquals(GatewayAuditOutcome.FAILURE, audited.get().outcome());
				assertTrue(audited.get().principal().isEmpty());
				assertEquals(
					GatewayErrorCodes.GatewayRequestRejected.name(),
					audited.get().failureCode().orElseThrow()
				);
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsNullAuthenticationFuture(Vertx vertx, VertxTestContext testContext) {
		GatewayRequestHooks hooks = new GatewayRequestHooks() {
			@Override
			public Future<GatewayPrincipal> authenticate(
				RoutingContext context,
				GatewayRequestMetadata request
			) {
				return null;
			}
		};
		GatewayRestVerticle gateway = new GatewayRestVerticle(
			new GatewayRestOptions().setPort(0),
			hooks
		);

		vertx.deployVerticle(gateway)
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gateway.actualPort(), "127.0.0.1", "/gateway/status")
				.compose(request -> request.send()))
			.onComplete(testContext.succeeding(response -> testContext.verify(() -> {
				assertEquals(403, response.statusCode());
				testContext.completeNow();
			})));
	}

	private static GatewayPrincipal authenticatedPrincipal() {
		return GatewayPrincipal.builder()
			.withSubject("operator-17")
			.withAuthenticationScheme("test")
			.withAuthenticated(true)
			.withRoles(List.of("reader"))
			.build();
	}
}
