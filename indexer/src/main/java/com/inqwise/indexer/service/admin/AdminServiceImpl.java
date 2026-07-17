package com.inqwise.indexer.service.admin;

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

import io.vertx.core.Future;

public class AdminServiceImpl implements AdminService {
	private final DocumentStoreMetadataRepository repository;
	private final MetadataChangeNotifier metadataChangeNotifier;
	private final IndexerManagementService indexerManagementService;
	private final CommandService commandService;
	private final IndexerOperations indexerOperations;
	private final IndexerQueueManagementService queueManagementService;
	private final TargetManagementService targetManagementService;
	private final IndexerProvisioningService indexerProvisioning;

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
		this.repository = Objects.requireNonNull(repository, "repository");
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
		Objects.requireNonNull(queueResources, "queueResources");
		Objects.requireNonNull(targetDefinitionProvider, "targetDefinitionProvider");
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
			indexerDefinitionProvider,
			documentIndexResources,
			queueResources
		);
		IndexPublicationService indexPublicationService = new MetadataIndexPublicationService(
			repository,
			indexerDefinitionProvider,
			documentIndexResources,
			queueResources
		);
		this.targetManagementService = new MetadataTargetManagementService(
			repository,
			targetDefinitionProvider,
			indexerProvisioning,
			indexPublicationService,
			metadataChangeNotifier
		);
	}

	@Override
	public Future<AdminTargetListResult> listTargets(AdminTargetQuery query) {
		try {
			AdminTargetQuery resolved = query == null ? new AdminTargetQuery() : query;
			return repository.listTargets(resolved.toCatalogQuery())
				.map(targets -> new AdminTargetListResult().setTargets(targets.stream()
					.map(AdminTargetView::from)
					.toList()))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerListResult> listIndexers(AdminIndexerQuery query) {
		try {
			AdminIndexerQuery resolved = query == null ? new AdminIndexerQuery() : query;
			return repository.listIndexers(resolved.toMetadataQuery())
				.map(indexers -> new AdminIndexerListResult().setIndexers(indexers.stream()
					.map(AdminIndexerView::from)
					.toList()))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
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

			return targetManagementService.recoverProvisioning(new RecoverTargetProvisioningRequest(
				request.getTargetId(),
				request.getExpectedVersion()
			))
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
			return indexerManagementService.activate(new IndexerRuntimeStateRequest(
				indexerId,
				request.getExpectedVersion()
			)).compose(indexer -> loadIndexerResult(indexer.indexerId()))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerResult> deactivateIndexer(AdminIndexerLifecycleRequest request) {
		try {
			Integer indexerId = validateIndexerLifecycle(request);
			return indexerManagementService.deactivate(new IndexerRuntimeStateRequest(
				indexerId,
				request.getExpectedVersion()
			)).compose(indexer -> loadIndexerResult(indexer.indexerId()))
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
			return indexerOperations.markDeleting(new MarkIndexerDeletingRequest(
				indexerId,
				request.getExpectedVersion()
			)).compose(marked -> marked
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
				.map(indexer -> commandService.submit(new CleanupDeletingIndexerCommand(
					deletion.indexerId()
				)).map(ignored -> new AdminIndexerResult().setIndexer(
					AdminIndexerView.from(indexer)
				)))
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
					.map(indexer -> queueManagementService.reset(new ResetIndexerQueueRequest(
						indexerId,
						indexer.queueName(),
						request.getExpectedVersion()
					)))
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
				.compose(indexer -> metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
						indexer.indexerId(),
						indexer.targetId(),
						"indexer.create",
						indexer.version()
					)).map(ignored -> indexer))
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
			.map(view -> new AdminTargetResult().setTarget(view))
			.orElseThrow(() -> IndexerErrors.notFound("Target not found"));
	}

	private AdminIndexerResult indexerResult(Optional<IndexerRecord> found) {
		return found
			.map(AdminIndexerView::from)
			.map(view -> new AdminIndexerResult().setIndexer(view))
			.orElseThrow(() -> IndexerErrors.notFound("Indexer not found"));
	}

	private void validateCreateIndexer(AdminCreateIndexerRequest request) {
		if (request == null) {
			throw IndexerErrors.invalidRequest("Request is required");
		}

		if (request.getTargetId() == null) {
			throw IndexerErrors.invalidRequest("Target id is required");
		}

		if (request.getIndexName() == null || request.getIndexName().isBlank()) {
			throw IndexerErrors.invalidRequest("Index name is required");
		}

		if (request.getQueueName() == null || request.getQueueName().isBlank()) {
			throw IndexerErrors.invalidRequest("Queue name is required");
		}
	}
}
