package com.inqwise.indexer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerWebVerticleTest {
	@Test
	void servesTheSpaAndProxiesInternalApiRoutes(
		Vertx vertx,
		VertxTestContext testContext
	) {
		vertx.createHttpServer()
			.requestHandler(request -> request.response()
				.putHeader("content-type", "application/json")
				.end(new JsonObject().put("uri", request.uri()).encode()))
			.listen(0, "127.0.0.1")
			.compose(upstream -> {
				int upstreamPort = upstream.actualPort();
				IndexerWebOptions options = IndexerWebOptions.builder()
					.withPort(0)
					.withAdminPort(upstreamPort)
					.withTargetActionPort(upstreamPort)
					.withRuntimePort(upstreamPort)
					.withHealthPort(upstreamPort)
					.build();
				IndexerWebVerticle web = new IndexerWebVerticle(options);
				return vertx.deployVerticle(web)
					.map(ignored -> web);
			})
			.compose(web -> get(vertx, web.actualPort(), "/")
				.compose(response -> response.body().map(body -> {
					assertEquals(200, response.statusCode());
					assertTrue(body.toString().contains("Inqwise Indexer Console"));
					return null;
				}))
				.compose(ignored -> get(vertx, web.actualPort(), "/runtime"))
				.compose(response -> response.body().map(body -> {
					assertEquals(200, response.statusCode());
					assertTrue(body.toString().contains("<div id=\"root\"></div>"));
					return null;
				}))
				.compose(ignored -> get(
					vertx,
					web.actualPort(),
					"/api/admin/admin/targets?status=READY"
				))
				.compose(response -> response.body().map(body -> {
					assertEquals(200, response.statusCode());
					assertEquals(
						"/admin/targets?status=READY",
						body.toJsonObject().getString("uri")
					);
					return null;
				}))
				.compose(ignored -> get(
					vertx,
					web.actualPort(),
					"/api/unknown"
				))
				.compose(response -> {
					assertEquals(404, response.statusCode());
					return get(vertx, web.actualPort(), "/api");
				}))
			.onComplete(testContext.succeeding(response ->
				testContext.verify(() -> {
					assertEquals(404, response.statusCode());
					testContext.completeNow();
				})
			));
	}

	private static Future<HttpClientResponse> get(
		Vertx vertx,
		int port,
		String uri
	) {
		return vertx.createHttpClient()
			.request(HttpMethod.GET, port, "127.0.0.1", uri)
			.compose(request -> request.send());
	}
}
