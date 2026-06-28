package com.inqwise.indexer.service.admin;

import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.commands.ActivateIndexerCommand;
import com.inqwise.indexer.commands.ActivateIndexerCommandHandler;
import com.inqwise.indexer.commands.CreateIndexerCommand;
import com.inqwise.indexer.commands.CleanupDeletingIndexerCommand;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.DeactivateIndexerCommand;
import com.inqwise.indexer.commands.DeactivateIndexerCommandHandler;
import com.inqwise.indexer.commands.RecoverTargetProvisioningCommand;
import com.inqwise.indexer.commands.RecoverTargetProvisioningCommandHandler;
import com.inqwise.indexer.commands.ResetIndexerQueueCommand;
import com.inqwise.indexer.commands.ResetIndexerQueueCommandHandler;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.errors.IndexerErrors;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.operations.IndexerOperations;
import com.inqwise.indexer.operations.MarkIndexerDeletingRequest;
import com.inqwise.indexer.provisioning.CreateTargetOperation;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;
import com.inqwise.indexer.provisioning.IndexerProvisioningService;

import io.vertx.core.Future;

public class AdminServiceImpl implements AdminService {
	private final DocumentStoreMetadataRepository repository;
	private final IndexerLifecycleEventBus eventBus;
	private final RecoverTargetProvisioningCommandHandler recoverTargetProvisioning;
	private final ActivateIndexerCommandHandler activateIndexer;
	private final DeactivateIndexerCommandHandler deactivateIndexer;
	private final CommandService commandService;
	private final IndexerOperations indexerOperations;
	private final ResetIndexerQueueCommandHandler resetIndexerQueue;
	private final CreateTargetOperation createTarget;
	private final IndexerProvisioningService indexerProvisioning;

	public AdminServiceImpl(
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus,
		IndexerQueueResourceManager queueResources,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		CommandService commandService,
		IndexerOperations indexerOperations
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		Objects.requireNonNull(queueResources, "queueResources");
		Objects.requireNonNull(targetDefinitionProvider, "targetDefinitionProvider");
		this.recoverTargetProvisioning = new RecoverTargetProvisioningCommandHandler(
			repository,
			eventBus
		);
		this.activateIndexer = new ActivateIndexerCommandHandler(repository, eventBus);
		this.deactivateIndexer = new DeactivateIndexerCommandHandler(repository, eventBus);
		this.commandService = Objects.requireNonNull(commandService, "commandService");
		this.indexerOperations = Objects.requireNonNull(indexerOperations, "indexerOperations");
		this.resetIndexerQueue = new ResetIndexerQueueCommandHandler(
			repository,
			eventBus,
			queueResources,
			commandService
		);
		this.createTarget = new CreateTargetOperation(
			repository,
			targetDefinitionProvider,
			indexerDefinitionProvider,
			documentIndexResources,
			queueResources,
			eventBus
		);
		this.indexerProvisioning = new IndexerProvisioningService(
			repository,
			indexerDefinitionProvider,
			documentIndexResources,
			queueResources
		);
	}

	@Override
	public Future<AdminTargetListResult> listTargets(AdminTargetQuery query) {
		try {
			AdminTargetQuery resolved = query == null ? new AdminTargetQuery() : query;
			return repository.listTargets(resolved.toMetadataQuery())
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

			return recoverTargetProvisioning.handle(new RecoverTargetProvisioningCommand(
				request.getTargetId(),
				request.getExpectedVersion()
			))
				.compose(ignored -> repository.getTargetById(request.getTargetId()))
				.map(this::targetResult)
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerResult> activateIndexer(AdminIndexerLifecycleRequest request) {
		try {
			Integer indexerId = validateIndexerLifecycle(request);
			return activateIndexer.handle(new ActivateIndexerCommand(indexerId))
				.compose(ignored -> loadIndexerResult(indexerId))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<AdminIndexerResult> deactivateIndexer(AdminIndexerLifecycleRequest request) {
		try {
			Integer indexerId = validateIndexerLifecycle(request);
			return deactivateIndexer.handle(new DeactivateIndexerCommand(indexerId))
				.compose(ignored -> loadIndexerResult(indexerId))
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
				.map(indexer -> commandService.submit(new CleanupDeletingIndexerCommand(
					indexer.id()
				)).map(ignored -> new AdminIndexerResult().setIndexer(
					AdminIndexerView.from(indexer)
				)))
				.orElseGet(() -> Future.failedFuture(IndexerErrors.notFound(
					"Indexer not found"
				))))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
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
					.map(indexer -> resetIndexerQueue.handle(new ResetIndexerQueueCommand(
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

			return createTarget.create(request.toCommand())
				.map(target -> new AdminTargetResult().setTarget(AdminTargetView.from(target)))
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
				.map(indexer -> {
					eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
						indexer.id(),
						indexer.targetId(),
						CreateIndexerCommand.TYPE,
						indexer.version()
					));
					return indexer;
				})
				.map(indexer -> new AdminIndexerResult().setIndexer(AdminIndexerView.from(indexer)))
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

		return request.getIndexerId();
	}

	private Future<AdminIndexerResult> loadIndexerResult(Integer indexerId) {
		return repository.getIndexerById(indexerId)
			.map(this::indexerResult);
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

		if (request.getTargetName() == null || request.getTargetName().isBlank()) {
			throw IndexerErrors.invalidRequest("Target name is required");
		}

		if (request.getIndexName() == null || request.getIndexName().isBlank()) {
			throw IndexerErrors.invalidRequest("Index name is required");
		}
	}
}
