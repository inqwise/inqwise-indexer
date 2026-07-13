package com.inqwise.indexer.load.workflow;

import com.inqwise.indexer.load.api.ApproveLoadPublicationRequest;
import com.inqwise.indexer.load.api.CancelLoadRequest;
import com.inqwise.indexer.load.api.CreateLoadRequest;
import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;
import com.inqwise.indexer.load.api.LoadCompletion;
import com.inqwise.indexer.load.api.LoadManagementService;
import com.inqwise.indexer.load.api.LoadProviderRegistry;
import com.inqwise.indexer.load.api.LoadRequest;
import com.inqwise.indexer.load.api.LoadStopRequest;
import com.inqwise.indexer.load.api.LoadWriter;
import com.inqwise.indexer.load.api.RecoverCreatedLoadRequest;
import com.inqwise.indexer.load.api.StartLoadRequest;
import com.inqwise.indexer.load.commands.CleanupLoadCommand;
import com.inqwise.indexer.load.commands.LoadPublicationOrchestrator;
import com.inqwise.indexer.load.repository.IndexerLoadCompletion;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;
import com.inqwise.indexer.load.repository.InsertIndexerLoad;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadApproval;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadFailure;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadState;


import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.runtime.IndexerQueueClient;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.provisioning.GeneratedIndexerResources;
import com.inqwise.indexer.catalog.indexers.CreateIndexerOperation;
import com.inqwise.indexer.provisioning.IndexerResourceNameGenerator;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public final class MetadataLoadManagementService implements LoadManagementService {
	private static final String CHANGE_TYPE = "indexer.load.create";
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final IndexerQueueClient queueClient;
	private final IndexerLifecycleEventBus eventBus;
	private final CreateIndexerOperation createIndexer;
	private final CommandService commandService;
	private final LoadPublicationOrchestrator publicationOrchestrator;
	private final LoadProviderRegistry loadProviderRegistry;

	public MetadataLoadManagementService(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		IndexerQueueClient queueClient,
		LoadProviderRegistry loadProviderRegistry,
		IndexerLifecycleEventBus eventBus,
		CommandService commandService
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.queueClient = Objects.requireNonNull(queueClient, "queueClient");
		this.loadProviderRegistry = Objects.requireNonNull(loadProviderRegistry, "loadProviderRegistry");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		this.createIndexer = new CreateIndexerOperation(metadataRepository);
		this.commandService = Objects.requireNonNull(commandService, "commandService");
		this.publicationOrchestrator = new LoadPublicationOrchestrator(commandService);
	}

	@Override
	public Future<IndexerLoadRecord> create(CreateLoadRequest request) {
		Objects.requireNonNull(request, "request");
		return resolveExistingTarget(request)
			.compose(target -> loadRepository.getActiveByTargetId(target.id())
				.compose(active -> active
					.map(load -> Future.<TargetRecord>failedFuture(
						"Active indexer load already exists for target: " + target.id()
					))
					.orElseGet(() -> Future.succeededFuture(target))))
			.compose(target -> createLoadIndexer(request, target, resources(target))
				.compose(loadIndexer -> createLiveIndexer(request, target, loadIndexer)
				.compose(liveIndexer -> insertLoad(request, target, loadIndexer, liveIndexer)
					.compose(ignored -> publishCreatedEvents(loadIndexer, liveIndexer))
					.compose(ignored -> load(loadIndexer.id())))));
	}

	@Override
	public Future<IndexerLoadRecord> start(StartLoadRequest request) {
		Objects.requireNonNull(request, "request");
		return load(request.indexerId())
			.compose(load -> start(load, request))
			.compose(ignored -> load(request.indexerId()));
	}

	@Override
	public Future<IndexerLoadRecord> recoverCreated(RecoverCreatedLoadRequest request) {
		Objects.requireNonNull(request, "request");
		return load(request.indexerId())
			.compose(load -> validateRecoverable(load, request))
			.compose(load -> start(new StartLoadRequest(load.indexerId(), load.version())));
	}

	private Future<TargetRecord> resolveExistingTarget(CreateLoadRequest request) {
		return metadataRepository.getTargetById(request.targetId())
			.compose(found -> found
				.map(this::validateTargetReady)
				.orElseGet(() -> Future.failedFuture(
					"Target not found for load creation: " + request.targetId()
				)));
	}

	private Future<TargetRecord> validateTargetReady(TargetRecord target) {
		if (target.status() != TargetStatus.ACTIVE) {
			return Future.failedFuture("Target is not active: " + target.id());
		}
		if (target.provisioningState() != TargetProvisioningState.READY) {
			return Future.failedFuture(
				"Target provisioning is not ready: " + target.id() + " state "
					+ target.provisioningState()
			);
		}
		return Future.succeededFuture(target);
	}

	@Override
	public Future<IndexerLoadRecord> approvePublication(ApproveLoadPublicationRequest request) {
		Objects.requireNonNull(request, "request");
		return load(request.indexerId()).compose(load -> {
			if (isAppliedApproval(load, request)) {
				return publishApproval(load);
			}
			if (load.version() != request.expectedVersion()) {
				return Future.failedFuture(
					"Indexer load version conflict for id " + load.indexerId() + ": expected "
						+ request.expectedVersion() + " but was " + load.version()
				);
			}
			if (load.state() == IndexerLoadState.FAILED
				|| load.state() == IndexerLoadState.CANCELLED
				|| load.state() == IndexerLoadState.PUBLISHED) {
				return Future.failedFuture("Indexer load is not approvable: " + load.state());
			}
			if (!load.reviewRequired() || load.state() != IndexerLoadState.WAITING_FOR_REVIEW) {
				return Future.failedFuture("Indexer load is not waiting for review: " + load.state());
			}

			return loadRepository.approve(new UpdateIndexerLoadApproval(
				load.indexerId(),
				request.approvedAt(),
				request.approvedBy(),
				request.approvalReason(),
				load.version()
			)).compose(ignored -> load(load.indexerId()))
				.compose(this::publishApproval);
		});
	}

	@Override
	public Future<Void> cancel(CancelLoadRequest request) {
		Objects.requireNonNull(request, "request");
		return loadRepository.getByIndexerId(request.indexerId()).compose(found -> found
			.map(load -> cancel(load, request))
			.orElseGet(() -> cancelCompleted(request)));
	}

	private Future<Void> cancel(IndexerLoadRecord load, CancelLoadRequest request) {
		if (isAppliedCancellation(load, request)) {
			return submitCleanup(load);
		}
		if (load.version() != request.expectedVersion()) {
			return Future.failedFuture(
				"Indexer load version conflict for id " + load.indexerId() + ": expected "
					+ request.expectedVersion() + " but was " + load.version()
			);
		}
		if (load.state() == IndexerLoadState.PUBLISHED
			|| load.state() == IndexerLoadState.CANCELLED) {
			return Future.failedFuture("Indexer load is not cancellable: " + load.state());
		}

		return stopProviderIfStarted(load, request)
			.compose(ignored -> loadRepository.updateState(new UpdateIndexerLoadState(
				load.indexerId(),
				IndexerLoadState.CANCELLED,
				load.version()
			)))
			.compose(ignored -> load(load.indexerId()))
			.compose(this::submitCleanup);
	}

	private Future<Void> cancelCompleted(CancelLoadRequest request) {
		return loadRepository.getCompletionByIndexerId(request.indexerId())
			.compose(found -> found
				.filter(completion -> isAppliedCancellation(completion, request))
				.map(ignored -> Future.<Void>succeededFuture())
				.orElseGet(() -> Future.failedFuture(
					"Indexer load not found: " + request.indexerId()
				)));
	}

	private boolean isAppliedCancellation(IndexerLoadRecord load, CancelLoadRequest request) {
		return request.expectedVersion() < Long.MAX_VALUE
			&& load.version() == request.expectedVersion() + 1
			&& load.state() == IndexerLoadState.CANCELLED;
	}

	private boolean isAppliedCancellation(
		IndexerLoadCompletion completion,
		CancelLoadRequest request
	) {
		return request.expectedVersion() < Long.MAX_VALUE
			&& completion.terminalVersion() == request.expectedVersion() + 1
			&& completion.terminalState() == IndexerLoadState.CANCELLED;
	}

	private Future<Void> stopProviderIfStarted(IndexerLoadRecord load, CancelLoadRequest request) {
		if (load.state() == IndexerLoadState.CREATED) {
			return Future.succeededFuture();
		}
		return loadProviderRegistry.get(load.providerId())
			.compose(provider -> provider.stop(new LoadStopRequest(load.indexerId(), request.reason())));
	}

	private Future<Void> submitCleanup(IndexerLoadRecord load) {
		return commandService.submit(new CleanupLoadCommand(load.indexerId(), null));
	}

	private boolean isAppliedApproval(
		IndexerLoadRecord load,
		ApproveLoadPublicationRequest request
	) {
		return request.expectedVersion() < Long.MAX_VALUE
			&& load.version() == request.expectedVersion() + 1
			&& load.state() == IndexerLoadState.APPROVED
			&& Objects.equals(load.approvedAt(), request.approvedAt())
			&& Objects.equals(load.approvedBy(), request.approvedBy())
			&& Objects.equals(load.approvalReason(), request.approvalReason());
	}

	private Future<IndexerLoadRecord> publishApproval(IndexerLoadRecord load) {
		eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
			load.indexerId(),
			load.targetId(),
			"indexer.load.approve-publication",
			load.version()
		));
		return publicationOrchestrator.publishIfReady(load)
			.compose(ignored -> load(load.indexerId()));
	}

	private Future<IndexerLoadRecord> validateRecoverable(
		IndexerLoadRecord load,
		RecoverCreatedLoadRequest request
	) {
		if (load.state() != IndexerLoadState.CREATED) {
			return Future.failedFuture("Indexer load is not recoverable from state: " + load.state());
		}
		if (load.version() != request.expectedVersion()) {
			return Future.failedFuture(
				"Indexer load version conflict for id " + load.indexerId() + ": expected "
					+ request.expectedVersion() + " but was " + load.version()
			);
		}
		return Future.succeededFuture(load);
	}

	private Future<Void> start(IndexerLoadRecord load, StartLoadRequest request) {
		if (load.state() == IndexerLoadState.CREATED) {
			if (load.version() != request.expectedVersion()) {
				return Future.failedFuture(
					"Indexer load version conflict for id " + load.indexerId() + ": expected "
						+ request.expectedVersion() + " but was " + load.version()
				);
			}

			return loadRepository.updateState(new UpdateIndexerLoadState(
				load.indexerId(),
				IndexerLoadState.STARTING,
				load.version()
			)).compose(ignored -> load(load.indexerId()))
				.compose(updated -> publishStateChanged(updated)
					.compose(ignored -> startProvider(updated)));
		}

		if (load.state() == IndexerLoadState.STARTING) {
			return startProvider(load);
		}

		if (request.expectedVersion() < Long.MAX_VALUE
			&& load.version() == request.expectedVersion() + 2
			&& load.state() == IndexerLoadState.HISTORICAL_LOADING) {
			return Future.succeededFuture();
		}

		return Future.failedFuture("Indexer load is not startable: " + load.state());
	}

	private Future<Void> startProvider(IndexerLoadRecord load) {
		return metadataRepository.getIndexerById(load.indexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Load writer not found: " + load.indexerId())))
			.compose(loadWriter -> metadataRepository.getTargetById(load.targetId())
				.compose(found -> found
					.map(Future::succeededFuture)
					.orElseGet(() -> Future.failedFuture("Target not found: " + load.targetId())))
				.compose(target -> buildRequest(load, loadWriter, target)
					.compose(request -> loadProviderRegistry.get(load.providerId())
						.compose(provider -> provider.start(request, writer(load, loadWriter)))
						.compose(ignored -> markHistoricalLoading(load.indexerId()))
						.recover(error -> markProviderStartFailed(load.indexerId(), error)
							.compose(ignored -> Future.failedFuture(error))))));
	}

	private Future<LoadRequest> buildRequest(
		IndexerLoadRecord load,
		IndexerRecord loadWriter,
		TargetRecord target
	) {
		if (load.liveIndexerId() == null) {
			return Future.succeededFuture(loadRequest(load, loadWriter, target));
		}

		return metadataRepository.getIndexerById(load.liveIndexerId())
			.compose(found -> found
				.map(ignored -> Future.succeededFuture(loadRequest(load, loadWriter, target)))
				.orElseGet(() -> Future.failedFuture("Live writer not found: " + load.liveIndexerId())));
	}

	private LoadRequest loadRequest(
		IndexerLoadRecord load,
		IndexerRecord loadWriter,
		TargetRecord target
	) {
		return new LoadRequest(
			load.indexerId(),
			load.targetId(),
			load.liveIndexerId(),
			load.providerId(),
			target.targetName(),
			loadWriter.indexName(),
			loadWriter.queueName(),
			load.reloadStartAt(),
			load.liveReplayFrom(),
			load.sourceFrom(),
			load.sourceTo(),
			copy(load.sourceQuery()),
			load.sourcePlaybookId()
		);
	}

	private QueueLoadWriter writer(IndexerLoadRecord load, IndexerRecord loadWriter) {
		return new QueueLoadWriter(
			load.targetId(),
			load.indexerId(),
			loadWriter.indexName(),
			loadWriter.queueName(),
			queueClient,
			loadRepository
		);
	}

	private Future<Void> markHistoricalLoading(Integer indexerId) {
		return load(indexerId).compose(load -> {
			if (load.state() != IndexerLoadState.STARTING) {
				return Future.succeededFuture();
			}

			return loadRepository.updateState(new UpdateIndexerLoadState(
				load.indexerId(),
				IndexerLoadState.HISTORICAL_LOADING,
				load.version()
			)).compose(ignored -> load(load.indexerId()))
				.compose(this::publishStateChanged);
		});
	}

	private Future<Void> markProviderStartFailed(Integer indexerId, Throwable error) {
		return load(indexerId)
			.compose(load -> loadRepository.markFailed(new UpdateIndexerLoadFailure(
				indexerId,
				error == null || error.getMessage() == null
					? "Load provider failed to start"
					: error.getMessage(),
				null,
				load.version()
			)).compose(ignored -> load(indexerId))
				.compose(this::publishStateChanged));
	}

	private GeneratedIndexerResources resources(TargetRecord target) {
		return IndexerResourceNameGenerator.forTarget(target.targetName());
	}

	private Future<IndexerRecord> createLoadIndexer(
		CreateLoadRequest request,
		TargetRecord target,
		GeneratedIndexerResources resources
	) {
		return createIndexer.create(new InsertIndexer(
			resources.prefix(), target.id(), target.targetName(), resources.indexName(), resources.queueName(),
			IndexerType.INDEX, IndexerRole.LOAD_WRITER, IndexResourceOwnership.OWNER,
			IndexerRuntimeState.ACTIVE, PublicationState.UNPUBLISHED, MutationState.WRITABLE
		));
	}

	private Future<IndexerRecord> createLiveIndexer(
		CreateLoadRequest request,
		TargetRecord target,
		IndexerRecord loadIndexer
	) {
		if (request.liveWriterPolicy() != LiveWriterPolicy.CREATE_IMMEDIATELY) {
			return Future.succeededFuture();
		}
		return createIndexer.create(new InsertIndexer(
			loadIndexer.prefix(), target.id(), target.targetName(), loadIndexer.indexName(),
			loadIndexer.queueName() + "--live",
			IndexerType.INDEX, IndexerRole.LIVE_WRITER, IndexResourceOwnership.ATTACHED,
			IndexerRuntimeState.ACTIVE, PublicationState.UNPUBLISHED, MutationState.WRITABLE
		));
	}

	private Future<Void> insertLoad(
		CreateLoadRequest request,
		TargetRecord target,
		IndexerRecord loadIndexer,
		IndexerRecord liveIndexer
	) {
		return loadRepository.insert(new InsertIndexerLoad(
			loadIndexer.id(), target.id(), liveIndexer == null ? null : liveIndexer.id(),
			request.liveWriterPolicy(), request.providerId(), IndexerLoadState.CREATED,
			request.reloadStartAt(), request.liveReplayFrom(), request.sourceFrom(),
			request.sourceTo(), request.sourceQuery(), request.sourcePlaybookId(),
			request.reviewRequired()
		));
	}

	private Future<Void> publishCreatedEvents(IndexerRecord loadIndexer, IndexerRecord liveIndexer) {
		eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
			loadIndexer.id(), loadIndexer.targetId(), CHANGE_TYPE, loadIndexer.version()
		));
		if (liveIndexer != null) {
			eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
				liveIndexer.id(), liveIndexer.targetId(), CHANGE_TYPE, liveIndexer.version()
			));
		}
		return Future.succeededFuture();
	}

	private Future<Void> publishStateChanged(IndexerLoadRecord load) {
		eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
			load.indexerId(),
			load.targetId(),
			"indexer.load.start",
			load.version()
		));
		return Future.succeededFuture();
	}

	private JsonObject copy(JsonObject json) {
		return json == null ? null : json.copy();
	}

	private Future<IndexerLoadRecord> load(Integer indexerId) {
		return loadRepository.getByIndexerId(indexerId).compose(found -> found
			.map(Future::succeededFuture)
			.orElseGet(() -> Future.failedFuture("Indexer load not found: " + indexerId)));
	}
}
