package com.inqwise.indexer.node.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.load.rest.LoadQueryRestOptions;
import com.inqwise.indexer.load.rest.LoadRestOptions;
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
	void deploysGenericServicesWithoutConsumerProviders(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNodeApplicationVerticle application = new IndexerNodeApplicationVerticle();

		vertx.deployVerticle(
			application,
			new DeploymentOptions().setConfig(applicationConfig())
		)
			.compose(deploymentId -> vertx.createHttpClient()
				.request(HttpMethod.GET, application.actualWebPort(), "127.0.0.1", "/")
				.map(request -> new RequestDeployment(request, deploymentId)))
			.compose(value -> value.request().send()
				.map(response -> new ResponseDeployment(response, value.deploymentId())))
			.compose(value -> value.response().body().map(body -> {
				var response = value.response();
				assertEquals(200, response.statusCode());
				assertTrue(body.toString().contains("Inqwise Indexer Console"));
				assertEquals(-1, application.actualReportsRestPort());
				assertTrue(application.actualLoadRestPort() > 0);
				assertTrue(application.actualLoadQueryRestPort() > 0);
				return value.deploymentId();
			}))
			.compose(vertx::undeploy)
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private record RequestDeployment(
		io.vertx.core.http.HttpClientRequest request,
		String deploymentId
	) {
	}

	private record ResponseDeployment(
		io.vertx.core.http.HttpClientResponse response,
		String deploymentId
	) {
	}

	@Test
	void skipsLoadCompositionWhenDisabled(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNodeApplicationVerticle application = new IndexerNodeApplicationVerticle();
		JsonObject config = applicationConfig()
			.put(LoadDeploymentOptions.CONFIG_KEY, new JsonObject().put("enabled", false));

		vertx.deployVerticle(application, new DeploymentOptions().setConfig(config))
			.compose(deploymentId -> vertx.createHttpClient()
				.request(HttpMethod.GET, application.actualWebPort(), "127.0.0.1", "/")
				.compose(request -> request.send())
				.compose(response -> response.body().map(body -> deploymentId)))
			.compose(deploymentId -> {
				assertEquals(-1, application.actualLoadRestPort());
				assertEquals(-1, application.actualLoadQueryRestPort());
				return vertx.undeploy(deploymentId);
			})
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
			.put(LoadDeploymentOptions.CONFIG_KEY, new JsonObject().put("enabled", true))
			.put(LoadRestOptions.CONFIG_KEY, new JsonObject()
				.put("host", "127.0.0.1")
				.put("port", 0))
			.put(LoadQueryRestOptions.CONFIG_KEY, new JsonObject()
				.put("host", "127.0.0.1")
				.put("port", 0))
			.put("web", new JsonObject()
				.put("host", "127.0.0.1")
				.put("port", 0));
	}
}
