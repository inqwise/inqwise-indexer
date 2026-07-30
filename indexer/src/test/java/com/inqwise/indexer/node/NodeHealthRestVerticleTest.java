package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class NodeHealthRestVerticleTest {
	@Test
	void reportsLivenessAndReadinessIndependently(
		Vertx vertx,
		VertxTestContext testContext
	) {
		AtomicBoolean ready = new AtomicBoolean();
		NodeHealthRestVerticle health = new NodeHealthRestVerticle(
			NodeHealthRestOptions.builder().withPort(0).build(),
			ready::get
		);

		vertx.deployVerticle(health)
			.compose(ignored -> status(vertx, health.actualPort(), NodeHealthRestVerticle.LIVE_PATH))
			.compose(status -> {
				assertEquals(204, status);
				return status(vertx, health.actualPort(), NodeHealthRestVerticle.READY_PATH);
			})
			.compose(status -> {
				assertEquals(503, status);
				ready.set(true);
				return status(vertx, health.actualPort(), NodeHealthRestVerticle.READY_PATH);
			})
			.onComplete(testContext.succeeding(status -> testContext.verify(() -> {
				assertEquals(200, status);
				testContext.completeNow();
			})));
	}

	private static Future<Integer> status(Vertx vertx, int port, String path) {
		return vertx.createHttpClient()
			.request(HttpMethod.GET, port, "127.0.0.1", path)
			.compose(request -> request.send())
			.compose(response -> {
				int statusCode = response.statusCode();
				if (statusCode == 204) {
					return Future.succeededFuture(statusCode);
				}
				return response.body().map(body -> {
					assertEquals(
						statusCode == 200 ? "UP" : "DOWN",
						body.toJsonObject().getString("outcome")
					);
					return statusCode;
				});
			});
	}
}
