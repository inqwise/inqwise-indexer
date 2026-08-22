package com.inqwise.indexer.node.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.load.rest.LoadQueryRestOptions;
import com.inqwise.indexer.load.rest.LoadQueryRestVerticle;
import com.inqwise.indexer.load.rest.LoadRestOptions;
import com.inqwise.indexer.load.rest.LoadRestVerticle;
import com.inqwise.indexer.load.service.LoadQueryServiceVerticle;
import com.inqwise.indexer.load.service.LoadServiceVerticle;
import com.inqwise.indexer.node.application.monitoring.MicrometerIndexerEventPublisher;
import com.inqwise.indexer.query.rest.ReportsRestOptions;
import com.inqwise.indexer.query.rest.ReportsRestVerticle;
import com.inqwise.indexer.query.service.ReportDiscoveryServiceVerticle;
import com.inqwise.indexer.query.service.ReportsServiceVerticle;
import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.web.IndexerWebOptions;
import com.inqwise.indexer.web.IndexerWebVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.backends.BackendRegistries;

public final class IndexerNodeApplicationVerticle extends AbstractVerticle {
	public static final String WEB_CONFIG = "web";

	private final IndexerApplicationProvider provider;
	private IndexerApplicationRuntime runtime;
	private IndexerWebVerticle web;
	private final List<String> reportDeploymentIds = new ArrayList<>();
	private String reportDiscoveryDeploymentId;
	private ReportsRestVerticle neutralReportsRest;
	private String neutralReportsRestDeploymentId;
	private String loadQueryServiceDeploymentId;
	private String loadQueryRestDeploymentId;
	private LoadQueryRestVerticle loadQueryRest;
	private String loadServiceDeploymentId;
	private String loadRestDeploymentId;
	private LoadRestVerticle loadRest;

	public IndexerNodeApplicationVerticle() {
		this(new DefaultIndexerApplicationProvider());
	}

	public IndexerNodeApplicationVerticle(IndexerApplicationProvider provider) {
		this.provider = Objects.requireNonNull(provider, "provider");
	}

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
		runtime = provider.create(
			vertx,
			applicationConfig.copy(),
			eventPublisher,
			operationalMetrics
		);
		web = new IndexerWebVerticle(IndexerWebOptions.from(
			applicationConfig.getJsonObject(WEB_CONFIG, new JsonObject())
		));
		ReportsRestOptions neutralReportRestOptions =
			ReportsRestOptions.from(applicationConfig);
		LoadQueryRestOptions loadQueryRestOptions =
			LoadQueryRestOptions.from(applicationConfig);
		LoadRestOptions loadRestOptions = new LoadRestOptions(
			applicationConfig.getJsonObject(LoadRestOptions.CONFIG_KEY, new JsonObject())
		);

		runtime.start()
			.compose(ignored -> deployLoad(loadRestOptions, loadQueryRestOptions))
			.compose(ignored -> deployReports())
			.compose(ignored -> deployReportsRest(neutralReportRestOptions))
			.compose(ignored -> vertx.deployVerticle(web))
			.map((Void) null)
			.recover(this::rollbackStartup)
			.onComplete(startPromise);
	}

	private Future<Void> deployLoad(
		LoadRestOptions loadRestOptions,
		LoadQueryRestOptions loadQueryRestOptions
	) {
		if (!runtime.loadEnabled()) {
			return Future.succeededFuture();
		}
		return deployLoadManagement(loadRestOptions)
			.compose(ignored -> deployLoadQuery(loadQueryRestOptions));
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		stopNode().onComplete(stopPromise);
	}

	int actualWebPort() {
		return web == null ? -1 : web.actualPort();
	}

	int actualReportsRestPort() {
		return neutralReportsRest == null ? -1 : neutralReportsRest.actualPort();
	}

	int actualLoadRestPort() {
		return loadRest == null ? -1 : loadRest.actualPort();
	}

	int actualLoadQueryRestPort() {
		return loadQueryRest == null ? -1 : loadQueryRest.actualPort();
	}

	private Future<Void> stopNode() {
		return undeployLoadQuery()
			.compose(ignored -> undeployLoadManagement())
			.compose(ignored -> undeployReportsRest())
			.compose(ignored -> undeployReports())
			.compose(ignored -> runtime == null ? Future.succeededFuture() : runtime.stop());
	}

	private Future<Void> deployLoadManagement(LoadRestOptions options) {
		return vertx.deployVerticle(new LoadServiceVerticle(runtime.loadManagement()))
			.onSuccess(id -> loadServiceDeploymentId = id)
			.compose(ignored -> {
				loadRest = new LoadRestVerticle(options);
				return vertx.deployVerticle(loadRest)
					.onSuccess(id -> loadRestDeploymentId = id);
			})
			.mapEmpty();
	}

	private Future<Void> undeployLoadManagement() {
		Future<Void> stopped = loadRestDeploymentId == null
			? Future.succeededFuture()
			: vertx.undeploy(loadRestDeploymentId).recover(error -> Future.succeededFuture());
		loadRestDeploymentId = null;
		loadRest = null;
		return stopped.compose(ignored -> {
			if (loadServiceDeploymentId == null) {
				return Future.succeededFuture();
			}
			String id = loadServiceDeploymentId;
			loadServiceDeploymentId = null;
			return vertx.undeploy(id).recover(error -> Future.succeededFuture());
		});
	}

	private Future<Void> deployLoadQuery(
		LoadQueryRestOptions options
	) {
		return vertx.deployVerticle(new LoadQueryServiceVerticle(
			runtime.loadQuery()
		))
			.onSuccess(id -> loadQueryServiceDeploymentId = id)
			.compose(ignored -> {
				loadQueryRest = new LoadQueryRestVerticle(options);
				return vertx.deployVerticle(loadQueryRest)
					.onSuccess(id -> loadQueryRestDeploymentId = id);
			})
			.mapEmpty();
	}

	private Future<Void> undeployLoadQuery() {
		Future<Void> stopped = loadQueryRestDeploymentId == null
			? Future.succeededFuture()
			: vertx.undeploy(loadQueryRestDeploymentId).recover(error -> Future.succeededFuture());
		loadQueryRestDeploymentId = null;
		loadQueryRest = null;
		return stopped.compose(ignored -> {
			if (loadQueryServiceDeploymentId == null) {
				return Future.succeededFuture();
			}
			String id = loadQueryServiceDeploymentId;
			loadQueryServiceDeploymentId = null;
			return vertx.undeploy(id).recover(error -> Future.succeededFuture());
		});
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

	private Future<Void> deployReports() {
		return vertx.deployVerticle(new ReportDiscoveryServiceVerticle(
			runtime.reports().catalog()
		)).onSuccess(id -> reportDiscoveryDeploymentId = id)
			.compose(ignored -> vertx.deployVerticle(new ReportsServiceVerticle(
				runtime.reports().service()
			)).onSuccess(reportDeploymentIds::add))
			.mapEmpty();
	}

	private Future<Void> undeployReports() {
		List<String> deployments = List.copyOf(reportDeploymentIds);
		reportDeploymentIds.clear();
		Future<Void> stopped = Future.succeededFuture();
		for (int index = deployments.size() - 1; index >= 0; index--) {
			String deploymentId = deployments.get(index);
			stopped = stopped.compose(ignored -> vertx.undeploy(deploymentId)
				.recover(error -> Future.succeededFuture()));
		}
		return stopped.compose(ignored -> {
			if (reportDiscoveryDeploymentId == null) {
				return Future.succeededFuture();
			}
			String deploymentId = reportDiscoveryDeploymentId;
			reportDiscoveryDeploymentId = null;
			return vertx.undeploy(deploymentId).recover(error -> Future.succeededFuture());
		});
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

	private Future<Void> rollbackStartup(Throwable startupError) {
		return stopNode().transform(stopResult -> {
			if (stopResult.failed()) {
				startupError.addSuppressed(stopResult.cause());
			}
			return Future.failedFuture(startupError);
		});
	}
}
