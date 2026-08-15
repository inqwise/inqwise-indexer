package com.inqwise.indexer.node.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.inqwise.coordination.LocalExclusiveFlowCoordinator;
import com.inqwise.events.EventPublisher;
import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.example.hn.actions.HackerNewsTargetActionPreparer;
import com.inqwise.indexer.example.hn.reports.HackerNewsReportCatalog;
import com.inqwise.indexer.example.hn.reports.HackerNewsReportConstants;
import com.inqwise.indexer.example.hn.reports.local.LocalHackerNewsReports;
import com.inqwise.indexer.example.hn.reports.rest.HackerNewsReportsRestOptions;
import com.inqwise.indexer.example.hn.reports.rest.HackerNewsReportsRestVerticle;
import com.inqwise.indexer.metadata.RepositoryPublishedIndexResolver;
import com.inqwise.indexer.load.adapters.local.InMemoryIndexerLoadRepository;
import com.inqwise.indexer.load.adapters.local.InMemoryLoadProviderRegistry;
import com.inqwise.indexer.load.adapters.metadata.MetadataLazyLiveWriterCatalog;
import com.inqwise.indexer.load.adapters.metadata.MetadataLoadCreationCatalog;
import com.inqwise.indexer.load.adapters.metadata.MetadataLoadPublicationRepository;
import com.inqwise.indexer.load.commands.LoadCommandHandlers;
import com.inqwise.indexer.load.rest.LoadQueryRestOptions;
import com.inqwise.indexer.load.rest.LoadQueryRestVerticle;
import com.inqwise.indexer.load.rest.LoadRestOptions;
import com.inqwise.indexer.load.rest.LoadRestVerticle;
import com.inqwise.indexer.load.runtime.LoadIndexerPlugin;
import com.inqwise.indexer.load.service.LoadQueryServiceVerticle;
import com.inqwise.indexer.load.service.LoadServiceVerticle;
import com.inqwise.indexer.load.workflow.DefaultLoadManagementService;
import com.inqwise.indexer.load.workflow.RepositoryLoadQuery;
import com.inqwise.indexer.node.IndexerNode;
import com.inqwise.indexer.node.IndexerNodeOptions;
import com.inqwise.indexer.node.IndexerPluginContext;
import com.inqwise.indexer.node.IndexerPluginFactory;
import com.inqwise.indexer.node.application.monitoring.MicrometerIndexerEventPublisher;
import com.inqwise.indexer.node.application.monitoring.MicrometerReportOperationalMonitor;
import com.inqwise.indexer.providers.IndexerPlugins;
import com.inqwise.indexer.query.ConsumerReportExecutionContextResolver;
import com.inqwise.indexer.query.ReportCatalog;
import com.inqwise.indexer.runtime.RuntimeIndexerPublishingService;
import com.inqwise.indexer.query.ReportExecutionContext;
import com.inqwise.indexer.query.monitoring.ReportOperationalMonitor;
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
	private String loadQueryServiceDeploymentId;
	private String loadQueryRestDeploymentId;
	private LoadQueryRestVerticle loadQueryRest;
	private String loadServiceDeploymentId;
	private String loadRestDeploymentId;
	private LoadRestVerticle loadRest;
	private InMemoryIndexerLoadRepository loadRepository;
	private InMemoryLoadProviderRegistry loadProviders;
	private IndexerPluginContext loadPluginContext;
	private ReportOperationalMonitor reportMonitor = ReportOperationalMonitor.NOOP;

	@Override
	public void start(Promise<Void> startPromise) {
		JsonObject applicationConfig = config();
		MicrometerIndexerEventPublisher operationalMetrics = null;
		if (BackendRegistries.getDefaultNow() != null) {
			operationalMetrics = new MicrometerIndexerEventPublisher(
				BackendRegistries.getDefaultNow()
			);
			ReportCatalog reportCatalog = HackerNewsReportCatalog.create();
			reportMonitor = new MicrometerReportOperationalMonitor(
				BackendRegistries.getDefaultNow(),
				reportCatalog.presentations().stream()
					.map(presentation -> presentation.getName())
					.collect(java.util.stream.Collectors.toUnmodifiableSet())
			);
		}
		IndexerEventPublisher eventPublisher = operationalMetrics == null
			? IndexerEventPublisher.NOOP
			: operationalMetrics;
		HackerNewsActionsDeploymentOptions actionOptions =
			HackerNewsActionsDeploymentOptions.from(applicationConfig);
		LoadDeploymentOptions loadOptions = LoadDeploymentOptions.from(applicationConfig);
		if (loadOptions.enabled()) {
			loadRepository = new InMemoryIndexerLoadRepository();
			loadProviders = new InMemoryLoadProviderRegistry();
		}
		node = IndexerNode.create(
			vertx,
			new IndexerNodeOptions(applicationConfig),
			null,
			eventPublisher,
			operationalMetrics,
			actionPreparations(actionOptions),
			loadOptions.enabled() ? this::createLoadPlugins : IndexerPluginFactory.NONE
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
		LoadQueryRestOptions loadQueryRestOptions =
			LoadQueryRestOptions.from(applicationConfig);
		LoadRestOptions loadRestOptions = new LoadRestOptions(
			applicationConfig.getJsonObject(LoadRestOptions.CONFIG_KEY, new JsonObject())
		);

		node.start()
			.compose(ignored -> deployLoad(loadOptions, loadRestOptions, loadQueryRestOptions))
			.compose(ignored -> deployHackerNewsReports(reportOptions))
			.compose(ignored -> deployReportsRest(neutralReportRestOptions))
			.compose(ignored -> deployHackerNewsReportsRest(reportRestOptions))
			.compose(ignored -> vertx.deployVerticle(web))
			.map((Void) null)
			.recover(this::rollbackStartup)
			.onComplete(startPromise);
	}

	private IndexerPlugins createLoadPlugins(IndexerPluginContext context) {
		loadPluginContext = context;
		MetadataLoadPublicationRepository publications =
			new MetadataLoadPublicationRepository(context.repository());
		LoadCommandHandlers.register(
			context.commandEngine(),
			LoadCommandHandlers.Config.builder()
				.withPublicationRepository(publications)
				.withCleanupRepository(publications)
				.withLoadRepository(loadRepository)
				.withEventBus(context.lifecycleEventBus())
				.build()
		);
		return new IndexerPlugins(List.of(new LoadIndexerPlugin(
			new MetadataLazyLiveWriterCatalog(context.repository()),
			loadRepository,
			context.commandEngine(),
			EventPublisher.NOOP,
			new LocalExclusiveFlowCoordinator(),
			context.lifecycleEventBus()
		)));
	}

	private Future<Void> deployLoad(
		LoadDeploymentOptions loadOptions,
		LoadRestOptions loadRestOptions,
		LoadQueryRestOptions loadQueryRestOptions
	) {
		if (!loadOptions.enabled()) {
			return Future.succeededFuture();
		}
		return deployLoadManagement(loadRestOptions)
			.compose(ignored -> deployLoadQuery(loadRepository, loadQueryRestOptions));
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

	int actualLoadRestPort() {
		return loadRest == null ? -1 : loadRest.actualPort();
	}

	int actualLoadQueryRestPort() {
		return loadQueryRest == null ? -1 : loadQueryRest.actualPort();
	}

	InMemoryIndexerLoadRepository loadRepository() {
		return loadRepository;
	}

	IndexerPluginContext loadPluginContext() {
		return loadPluginContext;
	}

	IndexerNode node() {
		return node;
	}

	private Future<Void> stopNode() {
		return undeployLoadQuery()
			.compose(ignored -> undeployLoadManagement())
			.compose(ignored -> undeployHackerNewsReportsRest())
			.compose(ignored -> undeployReportsRest())
			.compose(ignored -> undeployReportDiscovery())
			.compose(ignored -> undeployHackerNewsReports())
			.compose(ignored -> node == null ? Future.succeededFuture() : node.stop());
	}

	private Future<Void> deployLoadManagement(LoadRestOptions options) {
		IndexerPluginContext context = java.util.Objects.requireNonNull(
			loadPluginContext,
			"loadPluginContext"
		);
		DefaultLoadManagementService management = new DefaultLoadManagementService(
			new MetadataLoadCreationCatalog(context.repository()),
			loadRepository,
			new RuntimeIndexerPublishingService(node.components().runtime()),
			loadProviders,
			context.lifecycleEventBus(),
			context.commandEngine()
		);
		return vertx.deployVerticle(new LoadServiceVerticle(management))
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
		InMemoryIndexerLoadRepository repository,
		LoadQueryRestOptions options
	) {
		return vertx.deployVerticle(new LoadQueryServiceVerticle(
			new RepositoryLoadQuery(repository)
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
						)),
						reportMonitor
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
