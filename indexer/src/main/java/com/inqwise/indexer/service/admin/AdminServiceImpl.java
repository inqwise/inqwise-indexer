package com.inqwise.indexer.service.admin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.cleanup.CleanupDeletingIndexerCommand;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.catalog.targets.TargetDefinitionProvider;
import com.inqwise.indexer.service.IndexerErrors;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.catalog.indexers.IndexerDeletionResult;
import com.inqwise.indexer.catalog.indexers.MarkIndexerDeletingRequest;
import com.inqwise.indexer.catalog.targets.MetadataTargetManagementService;
import com.inqwise.indexer.catalog.targets.RecoverTargetProvisioningRequest;
import com.inqwise.indexer.catalog.indexers.IndexerManagementService;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeStateRequest;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerManagementService;
import com.inqwise.indexer.operations.queues.IndexerQueueManagementService;
import com.inqwise.indexer.operations.queues.MetadataIndexerQueueManagementService;
import com.inqwise.indexer.operations.queues.ResetIndexerQueueRequest;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;
import com.inqwise.indexer.provisioning.IndexerProvisioningService;
import com.inqwise.indexer.provisioning.MetadataIndexerProvisioningService;
import com.inqwise.indexer.publication.IndexPublicationService;
import com.inqwise.indexer.publication.MetadataIndexPublicationService;
import com.inqwise.indexer.catalog.targets.TargetManagementService;
import com.inqwise.indexer.monitoring.IndexerOperationalMonitor;
import com.inqwise.indexer.publication.MonitoredIndexPublicationService;
import com.inqwise.indexer.routing.InvalidRouteCache;
import com.inqwise.indexer.routing.InvalidRouteInvalidation;
import com.inqwise.indexer.routing.InvalidRouteRecord;
import com.inqwise.indexer.routing.InvalidRouteSignature;
import com.inqwise.indexer.lifecycle.TargetInvalidationEntries;
import com.inqwise.indexer.lifecycle.TargetInvalidationRegistry;
import com.inqwise.indexer.hot.HotRoutingDiagnostics;

import io.vertx.core.Future;

public class AdminServiceImpl implements AdminService {
	private static final int MAX_HOT_TARGETS = 1_000;
	private static final int MAX_HOT_INDEXERS_PER_TARGET = 100;
	private final DocumentStoreMetadataRepository repository;
	private final MetadataChangeNotifier metadataChangeNotifier;
	private final IndexerManagementService indexerManagementService;
	private final CommandService commandService;
	private final IndexerOperations indexerOperations;
	private final IndexerQueueManagementService queueManagementService;
	private final TargetManagementService targetManagementService;
	private final IndexerProvisioningService indexerProvisioning;
	private final TargetDefinitionProvider targetDefinitionProvider;
	private final IndexerDefinitionProvider indexerDefinitionProvider;
	private final InvalidRouteCache invalidRouteCache;
	private final TargetInvalidationRegistry targetInvalidationRegistry;
	private final HotRoutingDiagnostics hotRoutingDiagnostics;
	private final AdminNodeStatusSource nodeStatusSource;
	private final AdminNodeRecovery nodeRecovery;
	private final AdminInfrastructureStatusSource infrastructureStatusSource;

	public AdminServiceImpl(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueResourceManager queueResources,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		CommandService commandService,
		IndexerOperations indexerOperations
	) {
		this(
			repository,
			metadataChangeNotifier,
			queueResources,
			targetDefinitionProvider,
			indexerDefinitionProvider,
			documentIndexResources,
			commandService,
			indexerOperations,
			IndexerOperationalMonitor.NOOP
		);
	}

	public AdminServiceImpl(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueResourceManager queueResources,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		CommandService commandService,
		IndexerOperations indexerOperations,
		IndexerOperationalMonitor monitor
	) {
		this(
			repository,
			metadataChangeNotifier,
			queueResources,
			targetDefinitionProvider,
			indexerDefinitionProvider,
			documentIndexResources,
			commandService,
			indexerOperations,
			monitor,
			EmptyInvalidRouteCache.INSTANCE,
			EmptyTargetInvalidationRegistry.INSTANCE,
			EmptyNodeStatusSource.INSTANCE,
			EmptyInfrastructureStatusSource.INSTANCE
		);
	}

	public AdminServiceImpl(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueResourceManager queueResources,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		CommandService commandService,
		IndexerOperations indexerOperations,
		IndexerOperationalMonitor monitor,
		InvalidRouteCache invalidRouteCache,
		TargetInvalidationRegistry targetInvalidationRegistry
	) {
		this(
			repository,
			metadataChangeNotifier,
			queueResources,
			targetDefinitionProvider,
			indexerDefinitionProvider,
			documentIndexResources,
			commandService,
			indexerOperations,
			monitor,
			invalidRouteCache,
			targetInvalidationRegistry,
			EmptyNodeStatusSource.INSTANCE,
			EmptyInfrastructureStatusSource.INSTANCE
		);
	}

	public AdminServiceImpl(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueResourceManager queueResources,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		CommandService commandService,
		IndexerOperations indexerOperations,
		IndexerOperationalMonitor monitor,
		InvalidRouteCache invalidRouteCache,
		TargetInvalidationRegistry targetInvalidationRegistry,
		AdminNodeStatusSource nodeStatusSource
	) {
		this(
			repository,
			metadataChangeNotifier,
			queueResources,
			targetDefinitionProvider,
			indexerDefinitionProvider,
			documentIndexResources,
			commandService,
			indexerOperations,
			monitor,
			invalidRouteCache,
			targetInvalidationRegistry,
			nodeStatusSource,
			EmptyInfrastructureStatusSource.INSTANCE
		);
	}

	public AdminServiceImpl(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueResourceManager queueResources,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		CommandService commandService,
		IndexerOperations indexerOperations,
		IndexerOperationalMonitor monitor,
		InvalidRouteCache invalidRouteCache,
		TargetInvalidationRegistry targetInvalidationRegistry,
		AdminNodeStatusSource nodeStatusSource,
		AdminInfrastructureStatusSource infrastructureStatusSource
	) {
		this(
			repository,
			metadataChangeNotifier,
			queueResources,
			targetDefinitionProvider,
			indexerDefinitionProvider,
			documentIndexResources,
			commandService,
			indexerOperations,
			monitor,
			invalidRouteCache,
			targetInvalidationRegistry,
			nodeStatusSource,
			infrastructureStatusSource,
			AdminNodeRecovery.NONE
		);
	}

	public AdminServiceImpl(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueResourceManager queueResources,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		CommandService commandService,
		IndexerOperations indexerOperations,
		IndexerOperationalMonitor monitor,
		InvalidRouteCache invalidRouteCache,
		TargetInvalidationRegistry targetInvalidationRegistry,
		AdminNodeStatusSource nodeStatusSource,
		AdminInfrastructureStatusSource infrastructureStatusSource,
		AdminNodeRecovery nodeRecovery
	) {
		this(
			repository,
			metadataChangeNotifier,
			queueResources,
			targetDefinitionProvider,
			indexerDefinitionProvider,
			documentIndexResources,
			commandService,
			indexerOperations,
			monitor,
			invalidRouteCache,
			targetInvalidationRegistry,
			nodeStatusSource,
			infrastructureStatusSource,
			nodeRecovery,
			EmptyHotRoutingDiagnostics.INSTANCE
		);
	}

	public AdminServiceImpl(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueResourceManager queueResources,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		CommandService commandService,
		IndexerOperations indexerOperations,
		IndexerOperationalMonitor monitor,
		InvalidRouteCache invalidRouteCache,
		TargetInvalidationRegistry targetInvalidationRegistry,
		AdminNodeStatusSource nodeStatusSource,
		AdminInfrastructureStatusSource infrastructureStatusSource,
		AdminNodeRecovery nodeRecovery,
		HotRoutingDiagnostics hotRoutingDiagnostics
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
		Objects.requireNonNull(queueResources, "queueResources");
		this.targetDefinitionProvider = Objects.requireNonNull(
			targetDefinitionProvider,
			"targetDefinitionProvider"
		);
		this.indexerDefinitionProvider = Objects.requireNonNull(
			indexerDefinitionProvider,
			"indexerDefinitionProvider"
		);
		this.invalidRouteCache = invalidRouteCache == null
			? EmptyInvalidRouteCache.INSTANCE
			: invalidRouteCache;
		this.targetInvalidationRegistry = targetInvalidationRegistry == null
			? EmptyTargetInvalidationRegistry.INSTANCE
			: targetInvalidationRegistry;
		this.hotRoutingDiagnostics = hotRoutingDiagnostics == null
			? EmptyHotRoutingDiagnostics.INSTANCE
			: hotRoutingDiagnostics;
		this.nodeStatusSource = nodeStatusSource == null
			? EmptyNodeStatusSource.INSTANCE
			: nodeStatusSource;
		this.nodeRecovery = nodeRecovery == null
			? AdminNodeRecovery.NONE
			: nodeRecovery;
		this.infrastructureStatusSource = infrastructureStatusSource == null
			? EmptyInfrastructureStatusSource.INSTANCE
			: infrastructureStatusSource;
		this.indexerManagementService = new MetadataIndexerManagementService(
			repository,
			metadataChangeNotifier
		);
		this.commandService = Objects.requireNonNull(commandService, "commandService");
		this.indexerOperations = Objects.requireNonNull(indexerOperations, "indexerOperations");
		this.queueManagementService = new MetadataIndexerQueueManagementService(
			repository,
			metadataChangeNotifier,
			queueResources,
			commandService
		);
		this.indexerProvisioning = new MetadataIndexerProvisioningService(
			repository,
			this.indexerDefinitionProvider,
			documentIndexResources,
			queueResources
		);
		IndexPublicationService indexPublicationService =
			new MonitoredIndexPublicationService(
				new MetadataIndexPublicationService(
					repository,
					this.indexerDefinitionProvider,
					documentIndexResources,
					queueResources
				),
				Objects.requireNonNull(monitor, "monitor")
		);
		this.targetManagementService = new MetadataTargetManagementService(
			repository,
			this.targetDefinitionProvider,
			indexerProvisioning,
			indexPublicationService,
			metadataChangeNotifier
		);
	}

	@Override
	public Future<AdminTargetListResult> listTargets(AdminTargetQuery query) {
		try {
			AdminTargetQuery resolved = query == null ? AdminTargetQuery.builder().build() : query;
			return repository.listTargets(resolved.toCatalogQuery())
				.map(targets -> AdminTargetListResult.builder()
					.withTargets(targets.stream()
						.map(AdminTargetView::from)
						.toList())
					.build())
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerListResult> listIndexers(AdminIndexerQuery query) {
		try {
			AdminIndexerQuery resolved = query == null ? AdminIndexerQuery.builder().build() : query;
			return repository.listIndexers(resolved.toMetadataQuery())
				.map(indexers -> AdminIndexerListResult.builder()
					.withIndexers(indexers.stream()
						.map(AdminIndexerView::from)
						.toList())
					.build())
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminTargetDefinitionListResult> listTargetDefinitions() {
		try {
			return targetDefinitionProvider.list()
				.map(definitions -> AdminTargetDefinitionListResult.builder()
					.withTargetDefinitions(definitions.stream()
						.map(AdminTargetDefinitionView::from)
						.toList())
					.build())
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminTargetDefinitionResult> getTargetDefinition(String targetName) {
		try {
			if (targetName == null || targetName.isBlank()) {
				throw IndexerErrors.invalidRequest("Target name is required");
			}
			return targetDefinitionProvider.getByName(targetName)
				.map(found -> found
					.map(AdminTargetDefinitionView::from)
					.map(view -> AdminTargetDefinitionResult.builder()
						.withTargetDefinition(view)
						.build())
					.orElseThrow(() -> IndexerErrors.notFound("Target definition not found")))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerDefinitionListResult> listIndexerDefinitions() {
		try {
			return indexerDefinitionProvider.list()
				.map(definitions -> AdminIndexerDefinitionListResult.builder()
					.withIndexerDefinitions(definitions.entrySet().stream()
						.sorted(java.util.Map.Entry.comparingByKey())
						.map(entry -> AdminIndexerDefinitionView.from(
							entry.getKey(),
							entry.getValue()
						))
						.toList())
					.build())
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerDefinitionResult> getIndexerDefinition(String name) {
		try {
			if (name == null || name.isBlank()) {
				throw IndexerErrors.invalidRequest("Indexer definition name is required");
			}
			return indexerDefinitionProvider.list()
				.map(definitions -> Optional.ofNullable(definitions.get(name))
					.map(definition -> AdminIndexerDefinitionView.from(name, definition))
					.map(view -> AdminIndexerDefinitionResult.builder()
						.withIndexerDefinition(view)
						.build())
					.orElseThrow(() -> IndexerErrors.notFound("Indexer definition not found")))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminInvalidRouteListResult> listInvalidRoutes(int maxRoutes) {
		try {
			int limit = validatedMax(maxRoutes, "maxRoutes");
			var records = invalidRouteCache.list(limit + 1);
			boolean truncated = records.size() > limit;
			return Future.succeededFuture(AdminInvalidRouteListResult.builder()
				.withInvalidRoutes(records.stream()
					.limit(limit)
					.map(AdminInvalidRouteView::from)
					.toList())
				.withTruncated(truncated)
				.build());
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminTargetInvalidationListResult> listTargetInvalidations(int maxTargets) {
		try {
			int limit = validatedMax(maxTargets, "maxTargets");
			return targetInvalidationRegistry.listInvalidations(limit)
				.map(entries -> AdminTargetInvalidationListResult.builder()
					.withTargetInvalidations(entries.entries().stream()
						.map(AdminTargetInvalidationView::from)
						.toList())
					.withTruncated(entries.truncated())
					.build())
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminHotTargetListResult> listHotTargets(int maxTargets) {
		try {
			int limit = Math.min(
				validatedMax(maxTargets, "maxTargets"),
				MAX_HOT_TARGETS
			);
			var snapshot = hotRoutingDiagnostics.snapshot(
				limit,
				MAX_HOT_INDEXERS_PER_TARGET
			);
			return Future.succeededFuture(AdminHotTargetListResult.builder()
				.withHotTargets(snapshot.targets().stream()
					.map(AdminHotTargetView::from)
					.toList())
				.withTruncated(snapshot.truncated())
				.build());
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminNodeStatusResult> nodeStatus() {
		try {
			return Future.succeededFuture(nodeStatusSource.status());
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminNodeStatusResult> recoverNode() {
		try {
			return nodeRecovery.recover().map(ignored -> nodeStatusSource.status())
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminInfrastructureStatusResult> infrastructureStatus() {
		try {
			return Future.succeededFuture(infrastructureStatusSource.status());
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminTargetResult> getTarget(AdminTargetGetRequest request) {
		try {
			validateLookup(request == null ? null : request.getId(), request == null ? null : request.getUid());
			Future<Optional<TargetRecord>> target = request.getId() == null
				? repository.getTargetByUid(request.getUid())
				: repository.getTargetById(request.getId());
			return target.map(this::targetResult)
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerResult> getIndexer(AdminIndexerGetRequest request) {
		try {
			validateLookup(request == null ? null : request.getId(), request == null ? null : request.getUid());
			Future<Optional<IndexerRecord>> indexer = request.getId() == null
				? repository.getIndexerByUid(request.getUid())
				: repository.getIndexerById(request.getId());
			return indexer.map(this::indexerResult)
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminTargetResult> recoverTargetProvisioning(
		AdminRecoverTargetProvisioningRequest request
	) {
		try {
			if (request == null || request.getTargetId() == null) {
				throw IndexerErrors.invalidRequest("Target id is required");
			}

			return targetManagementService.recoverProvisioning(RecoverTargetProvisioningRequest.builder()
				.withTargetId(request.getTargetId())
				.withExpectedVersion(request.getExpectedVersion())
				.build())
				.compose(target -> loadTargetResult(target.targetId()))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerResult> activateIndexer(AdminIndexerLifecycleRequest request) {
		try {
			Integer indexerId = validateIndexerLifecycle(request);
			return indexerManagementService.activate(IndexerRuntimeStateRequest.builder()
				.withIndexerId(indexerId)
				.withExpectedVersion(request.getExpectedVersion())
				.build()).compose(indexer -> loadIndexerResult(indexer.indexerId()))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerResult> deactivateIndexer(AdminIndexerLifecycleRequest request) {
		try {
			Integer indexerId = validateIndexerLifecycle(request);
			return indexerManagementService.deactivate(IndexerRuntimeStateRequest.builder()
				.withIndexerId(indexerId)
				.withExpectedVersion(request.getExpectedVersion())
				.build()).compose(indexer -> loadIndexerResult(indexer.indexerId()))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerResult> deleteIndexer(AdminDeleteIndexerRequest request) {
		try {
			if (request == null || request.getIndexerId() == null) {
				throw IndexerErrors.invalidRequest("Indexer id is required");
			}
			if (request.getExpectedVersion() == null) {
				throw IndexerErrors.invalidRequest("Expected version is required");
			}

			Integer indexerId = request.getIndexerId();
			return indexerOperations.markDeleting(MarkIndexerDeletingRequest.builder()
				.withIndexerId(indexerId)
				.withExpectedVersion(request.getExpectedVersion())
				.build()).compose(marked -> marked
				.map(this::submitIndexerCleanup)
				.orElseGet(() -> Future.failedFuture(IndexerErrors.notFound(
					"Indexer not found"
				))))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	private Future<AdminIndexerResult> submitIndexerCleanup(IndexerDeletionResult deletion) {
		return repository.getIndexerById(deletion.indexerId())
			.compose(found -> found
				.map(indexer -> commandService.submit(CleanupDeletingIndexerCommand.builder()
					.withIndexerId(deletion.indexerId())
					.build()).map(ignored -> AdminIndexerResult.builder()
						.withIndexer(AdminIndexerView.from(indexer))
						.build()))
				.orElseGet(() -> Future.failedFuture(IndexerErrors.notFound(
					"Indexer not found"
				))));
	}

	@Override
	public Future<AdminIndexerResult> resetIndexerQueue(AdminResetIndexerQueueRequest request) {
		try {
			if (request == null || request.getIndexerId() == null) {
				throw IndexerErrors.invalidRequest("Indexer id is required");
			}

			Integer indexerId = request.getIndexerId();
			return repository.getIndexerById(indexerId)
				.compose(found -> found
					.map(indexer -> queueManagementService.reset(
						ResetIndexerQueueRequest.builder()
							.withIndexerId(indexerId)
							.withExpectedQueueName(indexer.queueName())
							.withExpectedVersion(request.getExpectedVersion())
							.build()
					))
					.orElseGet(() -> Future.failedFuture(IndexerErrors.notFound(
						"Indexer not found"
					))))
				.compose(ignored -> loadIndexerResult(indexerId))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminTargetResult> createTarget(AdminCreateTargetRequest request) {
		try {
			if (request == null || request.getTargetName() == null || request.getTargetName().isBlank()) {
				throw IndexerErrors.invalidRequest("Target name is required");
			}

			if (request.getCreateIndexer() != null
				&& request.getCreateIndexer().getInitialPublicationMode() == null) {
				throw IndexerErrors.invalidRequest("Initial publication mode is required");
			}

			return targetManagementService.createTarget(request.toTargetRequest())
				.compose(target -> loadTargetResult(target.targetId()))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerResult> createIndexer(AdminCreateIndexerRequest request) {
		try {
			validateCreateIndexer(request);
			return indexerProvisioning.createIndexer(request.toProvisioningRequest())
				.compose(indexer -> metadataChangeNotifier.indexerChanged(
					IndexerMetadataChanged.builder()
						.withIndexerId(indexer.indexerId())
						.withTargetId(indexer.targetId())
						.withCommandType("indexer.create")
						.withVersion(indexer.version())
						.build()
				).map(ignored -> indexer))
				.compose(indexer -> loadIndexerResult(indexer.indexerId()))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	private void validateLookup(Integer id, String uid) {
		boolean hasId = id != null;
		boolean hasUid = uid != null && !uid.isBlank();
		if (hasId == hasUid) {
			throw IndexerErrors.invalidRequest("Provide exactly one lookup identity");
		}
	}

	private Integer validateIndexerLifecycle(AdminIndexerLifecycleRequest request) {
		if (request == null || request.getIndexerId() == null) {
			throw IndexerErrors.invalidRequest("Indexer id is required");
		}
		if (request.getExpectedVersion() == null) {
			throw IndexerErrors.invalidRequest("Expected version is required");
		}

		return request.getIndexerId();
	}

	private Future<AdminIndexerResult> loadIndexerResult(Integer indexerId) {
		return repository.getIndexerById(indexerId)
			.map(this::indexerResult);
	}

	private Future<AdminTargetResult> loadTargetResult(Integer targetId) {
		return repository.getTargetById(targetId)
			.map(this::targetResult);
	}

	private AdminTargetResult targetResult(Optional<TargetRecord> found) {
		return found
			.map(AdminTargetView::from)
			.map(view -> AdminTargetResult.builder().withTarget(view).build())
			.orElseThrow(() -> IndexerErrors.notFound("Target not found"));
	}

	private AdminIndexerResult indexerResult(Optional<IndexerRecord> found) {
		return found
			.map(AdminIndexerView::from)
			.map(view -> AdminIndexerResult.builder().withIndexer(view).build())
			.orElseThrow(() -> IndexerErrors.notFound("Indexer not found"));
	}

	private void validateCreateIndexer(AdminCreateIndexerRequest request) {
		if (request == null) {
			throw IndexerErrors.invalidRequest("Request is required");
		}

		if (request.getTargetId() == null) {
			throw IndexerErrors.invalidRequest("Target id is required");
		}

		if (request.getPrefix() == null || request.getPrefix().isBlank()) {
			throw IndexerErrors.invalidRequest("Indexer prefix is required");
		}

		if (request.getIndexName() == null || request.getIndexName().isBlank()) {
			throw IndexerErrors.invalidRequest("Index name is required");
		}

		if (request.getQueueName() == null || request.getQueueName().isBlank()) {
			throw IndexerErrors.invalidRequest("Queue name is required");
		}
	}

	private int validatedMax(int value, String name) {
		if (value <= 0) {
			throw IndexerErrors.invalidRequest(name + " must be positive");
		}
		return value;
	}

	private enum EmptyInvalidRouteCache implements InvalidRouteCache {
		INSTANCE;

		@Override
		public Optional<InvalidRouteRecord> find(InvalidRouteSignature signature) {
			return Optional.empty();
		}

		@Override
		public void record(InvalidRouteSignature signature, String reason) {
		}

		@Override
		public void invalidateMatching(InvalidRouteInvalidation invalidation) {
		}

		@Override
		public List<InvalidRouteRecord> list(int maxRoutes) {
			return List.of();
		}
	}

	private enum EmptyTargetInvalidationRegistry implements TargetInvalidationRegistry {
		INSTANCE;

		@Override
		public Future<Void> markInvalidated(Integer concreteTargetId) {
			return Future.succeededFuture();
		}

		@Override
		public Future<TargetInvalidationEntries> listInvalidations(int maxTargets) {
			return Future.succeededFuture(TargetInvalidationEntries.builder()
				.withEntries(List.of())
				.withTruncated(false)
				.build());
		}
	}

	private enum EmptyNodeStatusSource implements AdminNodeStatusSource {
		INSTANCE;

		@Override
		public AdminNodeStatusResult status() {
			return AdminNodeStatusResult.builder()
				.withStarted(false)
				.withReady(false)
				.withRecoveryOnly(false)
				.withStopping(false)
				.withClustered(false)
				.withDeploymentCount(0)
				.withControlPlaneDeployments(0)
				.withDataPlaneDeployments(0)
				.withInfrastructureDeployments(0)
				.withLifecycleEventNamespace("unknown")
				.withTargetInvalidationProvider("unknown")
				.withTargetInvalidationNamespace("unknown")
				.withTargetInvalidationMaxTargets(0)
				.withServices(List.of())
				.build();
		}
	}

	private enum EmptyInfrastructureStatusSource implements AdminInfrastructureStatusSource {
		INSTANCE;

		@Override
		public AdminInfrastructureStatusResult status() {
			return AdminInfrastructureStatusResult.builder()
				.withItems(List.of())
				.build();
		}
	}
}
