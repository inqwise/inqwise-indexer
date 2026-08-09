package com.inqwise.indexer.node.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.node.IndexerNodeOptions;
import com.inqwise.indexer.node.IndexerNodeComponents;
import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.example.hn.reports.DefaultHackerNewsReports;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorOrder;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryRequest;
import com.inqwise.indexer.example.hn.reports.HackerNewsReports;
import com.inqwise.indexer.example.hn.reports.HackerNewsReportConstants;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesReportDefinition;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesRequest;
import com.inqwise.indexer.example.hn.reports.rest.HackerNewsReportsRestOptions;
import com.inqwise.indexer.example.hn.model.HackerNewsDocument;
import com.inqwise.indexer.example.hn.model.HackerNewsDocumentCodec;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertManifest;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.provisioning.ManifestStatus;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.query.TypedReportExecutor;
import com.inqwise.indexer.query.service.ReportsServices;
import com.inqwise.indexer.service.action.TargetActionServices;
import com.inqwise.indexer.service.action.TargetActionSubmitRequest;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;
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
				assertEquals(0, application.reportDeploymentCount());
				assertEquals(-1, application.actualHackerNewsReportsRestPort());
				return null;
			}))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deploysConfiguredHackerNewsReportsService(
		Vertx vertx,
		VertxTestContext testContext
	) {
		String address = ReportsServices.address("application-test");
		IndexerNodeApplicationVerticle application = new IndexerNodeApplicationVerticle();
		JsonObject config = applicationConfig().put(
			HackerNewsReportsDeploymentOptions.CONFIG_KEY,
			new JsonObject()
				.put("enabled", true)
				.put("instances", 1)
				.put("address", address)
		).put(
			HackerNewsReportsRestOptions.CONFIG_KEY,
			new JsonObject()
				.put("enabled", true)
				.put("host", "127.0.0.1")
				.put("port", 0)
				.put("reports_address", address)
		);

		vertx.deployVerticle(application, new DeploymentOptions().setConfig(config))
			.compose(deploymentId -> seedPublishedStory(application.node().components())
				.map(deploymentId))
			.compose(deploymentId -> {
				HackerNewsReports reports = new DefaultHackerNewsReports(
					new TypedReportExecutor(ReportsServices.proxy(vertx, address))
				);
				return reports.stories(HackerNewsStoriesRequest.builder()
					.withFromInclusive(Instant.parse("2026-01-01T00:00:00Z"))
					.withToExclusive(Instant.parse("2026-01-03T00:00:00Z"))
					.withLimit(10)
					.build()).compose(result -> {
					assertEquals(List.of(42L), result.stories().stream()
						.map(story -> story.id())
						.toList());
					assertEquals("Published story", result.stories().getFirst().title());
					assertEquals(1, application.reportDeploymentCount());
					return reports.storyAuthors(HackerNewsAuthorSummaryRequest.builder()
						.withFromInclusive(Instant.parse("2026-01-01T00:00:00Z"))
						.withToExclusive(Instant.parse("2026-01-03T00:00:00Z"))
						.withLimit(10)
						.withOrderBy(HackerNewsAuthorOrder.TOTAL_SCORE)
						.build()).compose(authors -> {
						assertEquals("example", authors.authors().getFirst().author());
						assertEquals(75, authors.authors().getFirst().totalScore());
						return queryStoriesOverHttp(
							vertx,
							application.actualHackerNewsReportsRestPort()
						);
					}).compose(body -> {
						assertEquals("Published story", body.getJsonArray("stories")
							.getJsonObject(0).getString("title"));
						return queryAuthorsOverHttp(
							vertx,
							application.actualHackerNewsReportsRestPort()
						);
					}).compose(body -> {
						assertEquals("example", body.getJsonArray("authors")
							.getJsonObject(0).getString("author"));
						return vertx.undeploy(deploymentId);
					});
				});
			})
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(0, application.reportDeploymentCount());
				assertEquals(-1, application.actualHackerNewsReportsRestPort());
				testContext.completeNow();
			})));
	}

	private Future<JsonObject> queryStoriesOverHttp(Vertx vertx, int port) {
		JsonObject body = new JsonObject()
			.put("from_inclusive", "2026-01-01T00:00:00Z")
			.put("to_exclusive", "2026-01-03T00:00:00Z")
			.put("minimum_score", 50)
			.put("limit", 10);
		return vertx.createHttpClient()
			.request(
				HttpMethod.POST,
				port,
				"127.0.0.1",
				"/reports/hacker-news/stories"
			)
			.compose(request -> request
				.putHeader("content-type", "application/json")
				.send(body.encode()))
			.compose(response -> {
				assertEquals(200, response.statusCode());
				return response.body();
			})
			.map(buffer -> buffer.toJsonObject());
	}

	private Future<JsonObject> queryAuthorsOverHttp(Vertx vertx, int port) {
		JsonObject body = new JsonObject()
			.put("from_inclusive", "2026-01-01T00:00:00Z")
			.put("to_exclusive", "2026-01-03T00:00:00Z")
			.put("minimum_score", 50)
			.put("limit", 10)
			.put("order_by", "total_score");
		return vertx.createHttpClient()
			.request(
				HttpMethod.POST,
				port,
				"127.0.0.1",
				"/reports/hacker-news/story-authors"
			)
			.compose(request -> request
				.putHeader("content-type", "application/json")
				.send(body.encode()))
			.compose(response -> {
				assertEquals(200, response.statusCode());
				return response.body();
			})
			.map(buffer -> buffer.toJsonObject());
	}

	private Future<Void> seedPublishedStory(IndexerNodeComponents components) {
		String indexName = "hn-application-test";
		return components.repository().insertTarget(InsertTarget.builder()
			.withPrefix("test")
			.withTargetName(HackerNewsReportConstants.TARGET_NAME)
			.withStatus(TargetStatus.ACTIVE)
			.withProvisioningState(TargetProvisioningState.READY)
			.build()).compose(targetId -> components.repository().insertIndexer(
				InsertIndexer.builder()
					.withPrefix("test")
					.withTargetId(targetId)
					.withTargetName(HackerNewsReportConstants.TARGET_NAME)
					.withIndexName(indexName)
					.withQueueName("queue-" + indexName)
					.withType(IndexerType.INDEX)
					.withRole(IndexerRole.LIVE_WRITER)
					.withIndexOwnership(IndexResourceOwnership.OWNER)
					.withStatus(IndexerStatus.AVAILABLE)
					.withProvisioningState(IndexerProvisioningState.READY)
					.withRuntimeState(IndexerRuntimeState.ACTIVE)
					.withPublicationState(PublicationState.PUBLISHED)
					.withMutationState(MutationState.WRITABLE)
					.build()
			).compose(indexerId -> components.repository().insertManifest(
				InsertManifest.builder()
					.withPrefix("test")
					.withTargetId(targetId)
					.withIndexerId(indexerId)
					.withTargetName(HackerNewsReportConstants.TARGET_NAME)
					.withIndexName(indexName)
					.withSchemaName(HackerNewsStoriesReportDefinition.SCHEMA.name())
					.withSchemaVersion(HackerNewsStoriesReportDefinition.SCHEMA.version())
					.withStatus(ManifestStatus.ACTIVE)
					.build()
			))).compose(ignored -> ((InMemoryIndexerDocumentStore)
			components.documentIndexResources()).put(
				indexName,
				"42",
				new HackerNewsDocumentCodec().encode(HackerNewsDocument.builder()
					.withId(42)
					.withType("story")
					.withAuthor("example")
					.withTime(Instant.parse("2026-01-02T00:00:00Z").getEpochSecond())
					.withTitle("Published story")
					.withUrl("https://example.test/story")
					.withScore(75)
					.withDescendants(10)
					.build())
			));
	}

	@Test
	void hackerNewsReportsDeploymentIsDisabledByDefault() {
		HackerNewsReportsDeploymentOptions options =
			HackerNewsReportsDeploymentOptions.from(new JsonObject());

		assertFalse(options.enabled());
		assertEquals(1, options.instances());
		assertEquals(ReportsServices.DEFAULT_ADDRESS, options.address());
	}

	@Test
	void hackerNewsActionPreparationIsDisabledByDefault() {
		assertFalse(HackerNewsActionsDeploymentOptions.from(new JsonObject()).enabled());
	}

	@Test
	void rejectsMalformedHackerNewsActionBeforeRouting(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNodeApplicationVerticle application = new IndexerNodeApplicationVerticle();
		JsonObject config = applicationConfig();
		config.getJsonObject("services")
			.getJsonObject(IndexerNodeOptions.Services.TARGET_ACTION)
			.put("enabled", true);
		config.put(
			HackerNewsActionsDeploymentOptions.CONFIG_KEY,
			new JsonObject()
				.put("enabled", true)
				.put("target_name", HackerNewsReportConstants.TARGET_NAME)
		);

		vertx.deployVerticle(application, new DeploymentOptions().setConfig(config))
			.compose(deploymentId -> TargetActionServices.proxy(vertx)
				.submit(TargetActionSubmitRequest.builder()
					.withTargetName(HackerNewsReportConstants.TARGET_NAME)
					.withActions(List.of(IndexerActionItems.putDocument(
						"42",
						new JsonObject()
							.put("id", 42L)
							.put("type", "story")
							.put("source", HackerNewsReportConstants.SOURCE_NAME)
					)))
					.build())
				.transform(result -> {
					testContext.verify(() -> {
						assertTrue(result.failed());
						ServiceException failure = assertInstanceOf(
							ServiceException.class,
							result.cause()
						);
						assertTrue(failure.getMessage().contains("InvalidRequest"));
					});
					return vertx.undeploy(deploymentId);
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
