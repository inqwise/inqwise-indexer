package com.inqwise.indexer.node.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.node.IndexerNodeOptions;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.micrometer.MicrometerMetricsFactory;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.PrometheusRequestHandler;
import io.vertx.micrometer.backends.BackendRegistries;

@ExtendWith(VertxExtension.class)
class MicrometerMetricsIntegrationTest {
	private static final String METRICS_PATH = "/metrics";

	@Test
	void applicationPublishesCustomMetricsToPrometheus(
		Vertx vertx,
		VertxTestContext testContext
	) throws IOException {
		JsonObject metricsJson = deploymentMetricsOptions();
		MicrometerMetricsOptions metricsOptions = (MicrometerMetricsOptions)
			new MicrometerMetricsFactory().newOptions(metricsJson);
		assertTrue(metricsOptions.isEnabled());
		assertTrue(metricsOptions.getPrometheusOptions().isEnabled());
		assertTrue(metricsOptions.getPrometheusOptions().isStartEmbeddedServer());
		assertEquals(
			METRICS_PATH,
			metricsOptions.getPrometheusOptions().getEmbeddedServerEndpoint()
		);
		assertEquals(
			9090,
			metricsOptions.getPrometheusOptions().getEmbeddedServerOptions().getPort()
		);

		metricsOptions.getPrometheusOptions().setStartEmbeddedServer(false);
		PrometheusMeterRegistry registry =
			new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
		BackendRegistries.setupBackend(metricsOptions, registry);
		IndexerNodeApplicationVerticle application =
			new IndexerNodeApplicationVerticle();

		vertx.deployVerticle(
			application,
			new DeploymentOptions().setConfig(applicationConfig())
		)
			.compose(deploymentId -> vertx.createHttpServer()
				.requestHandler(PrometheusRequestHandler.create(registry, METRICS_PATH))
				.listen(0, "127.0.0.1")
				.compose(server -> vertx.createHttpClient()
					.request(
						HttpMethod.GET,
						server.actualPort(),
						"127.0.0.1",
						METRICS_PATH
					)
					.compose(request -> request.send())
					.compose(response -> response.body().map(body -> {
						assertEquals(200, response.statusCode());
						assertTrue(
							body.toString().contains(
								"inqwise_indexer_runtime_active"
							),
							"Prometheus response should contain application meters"
						);
						return null;
					}))
					.eventually(server::close))
				.compose(ignored -> vertx.undeploy(deploymentId)))
			.eventually(() -> {
				BackendRegistries.stop(metricsOptions.getRegistryName());
				return Future.succeededFuture();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private static JsonObject deploymentMetricsOptions() throws IOException {
		Path repositoryRoot = Path.of(System.getProperty("user.dir"));
		Path optionsPath = repositoryRoot.resolve("deployment/local/vertx-options.json");
		if (!Files.exists(optionsPath)) {
			optionsPath = repositoryRoot.resolve("../deployment/local/vertx-options.json")
				.normalize();
		}
		JsonObject options = new JsonObject(Files.readString(optionsPath));
		return options.getJsonObject("metricsOptions").copy();
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
