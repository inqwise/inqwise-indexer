package com.inqwise.indexer.node.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

import com.inqwise.coordination.LocalExclusiveFlowCoordinator;
import com.inqwise.events.EventPublisher;
import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.load.adapters.local.InMemoryIndexerLoadRepository;
import com.inqwise.indexer.load.adapters.local.InMemoryLoadProviderRegistry;
import com.inqwise.indexer.load.adapters.metadata.MetadataLazyLiveWriterCatalog;
import com.inqwise.indexer.load.adapters.metadata.MetadataLoadCreationCatalog;
import com.inqwise.indexer.load.adapters.metadata.MetadataLoadPublicationRepository;
import com.inqwise.indexer.load.api.LoadManagementService;
import com.inqwise.indexer.load.api.LoadQuery;
import com.inqwise.indexer.load.commands.LoadCommandHandlers;
import com.inqwise.indexer.load.runtime.LoadIndexerPlugin;
import com.inqwise.indexer.load.workflow.DefaultLoadManagementService;
import com.inqwise.indexer.load.workflow.RepositoryLoadQuery;
import com.inqwise.indexer.metadata.RepositoryPublishedIndexResolver;
import com.inqwise.indexer.monitoring.IndexerOperationalMonitor;
import com.inqwise.indexer.node.DefaultIndexerNodeComponentsFactory;
import com.inqwise.indexer.node.IndexerNode;
import com.inqwise.indexer.node.IndexerNodeComponents;
import com.inqwise.indexer.node.IndexerNodeOptions;
import com.inqwise.indexer.node.IndexerPluginContext;
import com.inqwise.indexer.node.IndexerPluginFactory;
import com.inqwise.indexer.node.application.monitoring.MicrometerReportOperationalMonitor;
import com.inqwise.indexer.providers.IndexerPlugins;
import com.inqwise.indexer.query.monitoring.ReportExecutionOutcome;
import com.inqwise.indexer.query.monitoring.ReportOperationalMonitor;
import com.inqwise.indexer.query.provider.ReportsProviderContext;
import com.inqwise.indexer.query.provider.ReportsProviders;
import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.runtime.RuntimeIndexerPublishingService;
import com.inqwise.indexer.service.action.TargetActionPreparationProvider;
import com.inqwise.indexer.service.action.TargetActionPreparationRegistry;
import com.inqwise.indexer.service.action.TargetActionPreparer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.backends.BackendRegistries;

public final class DefaultIndexerApplicationProvider
	implements IndexerApplicationProvider {
	@Override
	public IndexerApplicationRuntime create(
		Vertx vertx,
		JsonObject config,
		IndexerEventPublisher events,
		IndexerOperationalMonitor monitor
	) {
		IndexerNodeOptions options = new IndexerNodeOptions(config);
		LoadDeploymentOptions loadOptions = LoadDeploymentOptions.from(config);
		InMemoryIndexerLoadRepository loadRepository = loadOptions.enabled()
			? new InMemoryIndexerLoadRepository()
			: null;
		InMemoryLoadProviderRegistry loadProviders = loadOptions.enabled()
			? new InMemoryLoadProviderRegistry()
			: null;
		IndexerPluginFactory plugins = loadOptions.enabled()
			? context -> createLoadPlugins(context, loadRepository)
			: IndexerPluginFactory.NONE;
		IndexerNodeComponents components = new DefaultIndexerNodeComponentsFactory().create(
			vertx,
			options,
			events,
			monitor,
			null,
			plugins
		);
		IndexerNode node = new IndexerNode(
			vertx,
			options,
			components,
			null,
			monitor,
			actionPreparations()
		);
		DelegatingReportOperationalMonitor reportMonitor =
			new DelegatingReportOperationalMonitor();
		ReportsProviders reports = ReportsProviders.load(reportsContext(
			components,
			reportMonitor
		));
		if (BackendRegistries.getDefaultNow() != null
			&& !reports.catalog().descriptors().isEmpty()) {
			reportMonitor.setDelegate(new MicrometerReportOperationalMonitor(
				BackendRegistries.getDefaultNow(),
				reports.catalog().descriptors().stream()
					.map(descriptor -> descriptor.name())
					.collect(java.util.stream.Collectors.toUnmodifiableSet())
			));
		}
		LoadManagementService loadManagement = loadOptions.enabled()
			? new DefaultLoadManagementService(
				new MetadataLoadCreationCatalog(components.repository()),
				loadRepository,
				new RuntimeIndexerPublishingService(components.runtime()),
				loadProviders,
				components.lifecycleEventBus(),
				components.commandEngine()
			)
			: null;
		LoadQuery loadQuery = loadOptions.enabled()
			? new RepositoryLoadQuery(loadRepository)
			: null;
		return new Runtime(node, reports, loadManagement, loadQuery);
	}

	private ReportsProviderContext reportsContext(
		IndexerNodeComponents components,
		ReportOperationalMonitor monitor
	) {
		if (!(components.documentIndexResources()
			instanceof InMemoryIndexerDocumentStore documents)) {
			throw new IllegalStateException(
				"Default application provider requires InMemoryIndexerDocumentStore"
			);
		}
		return ReportsProviderContext.builder()
			.withPublishedIndexes(new RepositoryPublishedIndexResolver(
				components.repository()
			))
			.withDocuments(documents::documents)
			.withMonitor(monitor)
			.build();
	}

	private IndexerPlugins createLoadPlugins(
		IndexerPluginContext context,
		InMemoryIndexerLoadRepository loadRepository
	) {
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

	private TargetActionPreparationRegistry actionPreparations() {
		Map<String, TargetActionPreparer> preparers = new LinkedHashMap<>();
		Map<String, TargetActionPreparationProvider> providers = new LinkedHashMap<>();
		ServiceLoader.load(TargetActionPreparationProvider.class).stream()
			.map(ServiceLoader.Provider::get)
			.sorted(Comparator.comparing(provider -> requireProviderId(provider.id())))
			.forEach(provider -> {
				String providerId = requireProviderId(provider.id());
				if (providers.putIfAbsent(providerId, provider) != null) {
					throw new IllegalArgumentException(
						"Duplicate target action preparation provider: " + providerId
					);
				}
				provider.preparers().forEach((targetName, preparer) -> {
					if (preparers.putIfAbsent(targetName, preparer) != null) {
						throw new IllegalArgumentException(
							"Duplicate target action preparer for target: " + targetName
						);
					}
				});
			});
		return preparers.isEmpty()
			? TargetActionPreparationRegistry.NONE
			: new TargetActionPreparationRegistry(preparers);
	}

	private static String requireProviderId(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				"Target action preparation provider id must not be blank"
			);
		}
		return value;
	}

	private static final class DelegatingReportOperationalMonitor
		implements ReportOperationalMonitor {
		private final AtomicReference<ReportOperationalMonitor> delegate =
			new AtomicReference<>(ReportOperationalMonitor.NOOP);

		private void setDelegate(ReportOperationalMonitor value) {
			delegate.set(Objects.requireNonNull(value, "delegate"));
		}

		@Override
		public void executionStarted(String reportName) {
			delegate.get().executionStarted(reportName);
		}

		@Override
		public void executionCompleted(
			String reportName,
			ReportExecutionOutcome outcome,
			long durationNanos
		) {
			delegate.get().executionCompleted(reportName, outcome, durationNanos);
		}
	}

	private record Runtime(
		IndexerNode node,
		ReportsProviders reports,
		LoadManagementService loadManagement,
		LoadQuery loadQuery
	) implements IndexerApplicationRuntime {
		@Override
		public Future<Void> start() {
			return node.start();
		}

		@Override
		public Future<Void> stop() {
			return node.stop();
		}

		@Override
		public boolean loadEnabled() {
			return loadManagement != null;
		}
	}
}
