package com.inqwise.indexer.node.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.node.IndexerNodeOptions;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerNodeApplicationVerticleTest {
	@Test
	void deploysWebInsideNodeApplication(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNodeApplicationVerticle application = new IndexerNodeApplicationVerticle();

		vertx.deployVerticle(
			application,
			new DeploymentOptions().setConfig(applicationConfig())
		)
			.compose(ignored -> vertx.createHttpClient()
				.request(
					HttpMethod.GET,
					application.actualWebPort(),
					"127.0.0.1",
					"/"
				))
			.compose(request -> request.send())
			.compose(response -> response.body().map(body -> {
				assertEquals(200, response.statusCode());
				assertTrue(body.toString().contains("Inqwise Indexer Console"));
				return null;
			}))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private static JsonObject applicationConfig() {
		JsonObject services = new JsonObject();
		for (String service : List.of(
			IndexerNodeOptions.Services.ADMIN,
			IndexerNodeOptions.Services.ADMIN_REST,
			IndexerNodeOptions.Services.TARGET_ACTION,
			IndexerNodeOptions.Services.TARGET_ACTION_REST,
			IndexerNodeOptions.Services.RUNTIME,
			IndexerNodeOptions.Services.RUNTIME_REST,
			IndexerNodeOptions.Services.HEALTH_REST,
			IndexerNodeOptions.Services.GATEWAY
		)) {
			services.put(service, new JsonObject().put("enabled", false));
		}
		return new JsonObject()
			.put("services", services)
			.put("web", new JsonObject()
				.put("host", "127.0.0.1")
				.put("port", 0));
	}
}
