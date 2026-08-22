package com.inqwise.indexer.example.hn.node.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.example.hn.reports.DefaultHackerNewsReports;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesRequest;
import com.inqwise.indexer.node.IndexerNodeOptions;
import com.inqwise.indexer.node.application.IndexerNodeApplicationVerticle;
import com.inqwise.indexer.node.application.LoadDeploymentOptions;
import com.inqwise.indexer.query.TypedReportExecutor;
import com.inqwise.indexer.query.service.ReportDiscoveryServices;
import com.inqwise.indexer.query.service.ReportsServices;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HackerNewsReportsProviderDiscoveryTest {
	@Test
	void discoversPackagedHackerNewsReportsAutomatically(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNodeApplicationVerticle application = new IndexerNodeApplicationVerticle();

		vertx.deployVerticle(
			application,
			new DeploymentOptions().setConfig(applicationConfig())
		)
			.compose(deploymentId -> ReportDiscoveryServices.proxy(vertx)
				.discover()
				.map(discovery -> {
					assertEquals(
						List.of(
							"hacker-news.stories",
							"hacker-news.story-authors"
						),
						discovery.getReports().stream()
							.map(report -> report.getName())
							.toList()
					);
					return deploymentId;
				}))
			.compose(deploymentId -> new DefaultHackerNewsReports(
				new TypedReportExecutor(ReportsServices.proxy(vertx))
			).stories(HackerNewsStoriesRequest.builder()
				.withFromInclusive(Instant.parse("2026-01-01T00:00:00Z"))
				.withToExclusive(Instant.parse("2026-01-02T00:00:00Z"))
				.withLimit(10)
				.build()).map(result -> {
					assertEquals(List.of(), result.stories());
					return deploymentId;
				}))
			.compose(vertx::undeploy)
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
			.put(LoadDeploymentOptions.CONFIG_KEY, new JsonObject().put("enabled", false))
			.put("web", new JsonObject()
				.put("host", "127.0.0.1")
				.put("port", 0));
	}
}
