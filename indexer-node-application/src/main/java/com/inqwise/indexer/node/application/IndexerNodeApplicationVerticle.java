package com.inqwise.indexer.node.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.example.hn.actions.HackerNewsTargetActionPreparer;
import com.inqwise.indexer.example.hn.reports.HackerNewsReportCatalog;
import com.inqwise.indexer.example.hn.reports.HackerNewsReportConstants;
import com.inqwise.indexer.example.hn.reports.local.LocalHackerNewsReports;
import com.inqwise.indexer.example.hn.reports.rest.HackerNewsReportsRestOptions;
import com.inqwise.indexer.example.hn.reports.rest.HackerNewsReportsRestVerticle;
import com.inqwise.indexer.metadata.RepositoryPublishedIndexResolver;
import com.inqwise.indexer.node.IndexerNode;
import com.inqwise.indexer.node.IndexerNodeOptions;
import com.inqwise.indexer.node.application.monitoring.MicrometerIndexerEventPublisher;
import com.inqwise.indexer.query.ConsumerReportExecutionContextResolver;
import com.inqwise.indexer.query.ReportExecutionContext;
import com.inqwise.indexer.query.rest.ReportsRestOptions;
import com.inqwise.indexer.query.rest.ReportsRestVerticle;
import com.inqwise.indexer.query.service.ReportDiscoveryServiceVerticle;
import com.inqwise.indexer.query.service.ReportsServiceVerticle;
import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.service.action.TargetActionPreparationRegistry;
import com.inqwise.indexer.web.IndexerWebOptions;
import com.inqwise.indexer.web.IndexerWebVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.backends.BackendRegistries;

public final class IndexerNodeApplicationVerticle extends AbstractVerticle {
	public static final String WEB_CONFIG = "web";

	private IndexerNode node;
	private IndexerWebVerticle web;
	private final List<String> reportDeploymentIds = new ArrayList<>();
	private String reportDiscoveryDeploymentId;
	private HackerNewsReportsRestVerticle reportsRest;
	private String reportsRestDeploymentId;
	private ReportsRestVerticle neutralReportsRest;
	private String neutralReportsRestDeploymentId;

	@Override
	public void start(Promise<Void> startPromise) {
		JsonObject applicationConfig = config();
		MicrometerIndexerEventPublisher operationalMetrics = null;
		if (BackendRegistries.getDefaultNow() != null) {
			operationalMetrics = new MicrometerIndexerEventPublisher(
				BackendRegistries.getDefaultNow()
			);
		}
		IndexerEventPublisher eventPublisher = operationalMetrics == null
			? IndexerEventPublisher.NOOP
			: operationalMetrics;
		HackerNewsActionsDeploymentOptions actionOptions =
			HackerNewsActionsDeploymentOptions.from(applicationConfig);
		node = IndexerNode.create(
			vertx,
			new IndexerNodeOptions(applicationConfig),
			null,
			eventPublisher,
			operationalMetrics,
			actionPreparations(actionOptions)
		);
		web = new IndexerWebVerticle(IndexerWebOptions.from(
			applicationConfig.getJsonObject(WEB_CONFIG, new JsonObject())
		));
		HackerNewsReportsDeploymentOptions reportOptions =
			HackerNewsReportsDeploymentOptions.from(applicationConfig);
		HackerNewsReportsRestOptions reportRestOptions =
			HackerNewsReportsRestOptions.from(applicationConfig);
		ReportsRestOptions neutralReportRestOptions =
			ReportsRestOptions.from(applicationConfig);

		node.start()
			.compose(ignored -> deployHackerNewsReports(reportOptions))
			.compose(ignored -> deployReportsRest(neutralReportRestOptions))
			.compose(ignored -> deployHackerNewsReportsRest(reportRestOptions))
			.compose(ignored -> vertx.deployVerticle(web))
			.map((Void) null)
			.recover(this::rollbackStartup)
			.onComplete(startPromise);
	}

	private TargetActionPreparationRegistry actionPreparations(
		HackerNewsActionsDeploymentOptions options
	) {
		if (!options.enabled()) {
			return TargetActionPreparationRegistry.NONE;
		}
		return new TargetActionPreparationRegistry(Map.of(
			options.targetName(),
			new HackerNewsTargetActionPreparer()
		));
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		stopNode().onComplete(stopPromise);
	}

	int actualWebPort() {
		return web == null ? -1 : web.actualPort();
	}

	int reportDeploymentCount() {
		return reportDeploymentIds.size();
	}

	int actualHackerNewsReportsRestPort() {
		return reportsRest == null ? -1 : reportsRest.actualPort();
	}

	int actualReportsRestPort() {
		return neutralReportsRest == null ? -1 : neutralReportsRest.actualPort();
	}

	IndexerNode node() {
		return node;
	}

	private Future<Void> stopNode() {
		return undeployHackerNewsReportsRest()
			.compose(ignored -> undeployReportsRest())
			.compose(ignored -> undeployReportDiscovery())
			.compose(ignored -> undeployHackerNewsReports())
			.compose(ignored -> node == null ? Future.succeededFuture() : node.stop());
	}

	private Future<Void> deployReportsRest(ReportsRestOptions options) {
		if (!options.enabled()) {
			return Future.succeededFuture();
		}
		neutralReportsRest = new ReportsRestVerticle(options);
		return vertx.deployVerticle(neutralReportsRest)
			.onSuccess(deploymentId -> neutralReportsRestDeploymentId = deploymentId)
			.mapEmpty();
	}

	private Future<Void> undeployReportsRest() {
		if (neutralReportsRestDeploymentId == null) {
			neutralReportsRest = null;
			return Future.succeededFuture();
		}
		String deploymentId = neutralReportsRestDeploymentId;
		neutralReportsRestDeploymentId = null;
		return vertx.undeploy(deploymentId)
			.recover(error -> Future.succeededFuture())
			.onComplete(ignored -> neutralReportsRest = null);
	}

	private Future<Void> deployHackerNewsReportsRest(
		HackerNewsReportsRestOptions options
	) {
		if (!options.enabled()) {
			return Future.succeededFuture();
		}
		reportsRest = new HackerNewsReportsRestVerticle(options);
		return vertx.deployVerticle(reportsRest)
			.onSuccess(deploymentId -> reportsRestDeploymentId = deploymentId)
			.mapEmpty();
	}

	private Future<Void> undeployHackerNewsReportsRest() {
		if (reportsRestDeploymentId == null) {
			reportsRest = null;
			return Future.succeededFuture();
		}
		String deploymentId = reportsRestDeploymentId;
		reportsRestDeploymentId = null;
		return vertx.undeploy(deploymentId)
			.recover(error -> Future.succeededFuture())
			.onComplete(ignored -> reportsRest = null);
	}

	private Future<Void> deployHackerNewsReports(
		HackerNewsReportsDeploymentOptions options
	) {
		if (!options.enabled()) {
			return Future.succeededFuture();
		}
		if (!(node.components().documentIndexResources()
			instanceof InMemoryIndexerDocumentStore documents)) {
			return Future.failedFuture(
				"Local Hacker News reports require InMemoryIndexerDocumentStore"
			);
		}

		Future<Void> deployed = vertx.deployVerticle(
			new ReportDiscoveryServiceVerticle(
				HackerNewsReportCatalog.create(),
				options.discoveryAddress()
			)
		).onSuccess(deploymentId -> reportDiscoveryDeploymentId = deploymentId).mapEmpty();
		for (int index = 0; index < options.instances(); index++) {
			deployed = deployed.compose(ignored -> vertx.deployVerticle(
				new ReportsServiceVerticle(
					LocalHackerNewsReports.create(
						new RepositoryPublishedIndexResolver(node.components().repository()),
						documents,
						new ConsumerReportExecutionContextResolver(java.util.Map.of(
							HackerNewsReportConstants.CONSUMER_NAME,
							ReportExecutionContext.builder().build()
						))
					),
					options.address()
				)
			).onSuccess(reportDeploymentIds::add).mapEmpty());
		}
		return deployed;
	}

	private Future<Void> undeployReportDiscovery() {
		if (reportDiscoveryDeploymentId == null) {
			return Future.succeededFuture();
		}
		String deploymentId = reportDiscoveryDeploymentId;
		reportDiscoveryDeploymentId = null;
		return vertx.undeploy(deploymentId).recover(error -> Future.succeededFuture());
	}

	private Future<Void> undeployHackerNewsReports() {
		List<String> deployments = List.copyOf(reportDeploymentIds);
		reportDeploymentIds.clear();
		Future<Void> undeployed = Future.succeededFuture();
		for (int index = deployments.size() - 1; index >= 0; index--) {
			String deploymentId = deployments.get(index);
			undeployed = undeployed.compose(ignored -> vertx.undeploy(deploymentId)
				.recover(error -> Future.succeededFuture()));
		}
		return undeployed;
	}

	private Future<Void> rollbackStartup(Throwable startupError) {
		return stopNode().transform(stopResult -> {
			if (stopResult.failed()) {
				startupError.addSuppressed(stopResult.cause());
			}
			return Future.failedFuture(startupError);
		});
	}
}
