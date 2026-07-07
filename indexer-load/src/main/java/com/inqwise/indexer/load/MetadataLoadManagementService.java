package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.provisioning.CreateIndexerOperation;

import io.vertx.core.Future;

public final class MetadataLoadManagementService implements LoadManagementService {
	private static final String CHANGE_TYPE = "indexer.load.create";
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final IndexerLifecycleEventBus eventBus;
	private final CreateIndexerOperation createIndexer;
	private final CommandService commandService;
	private final LoadPublicationOrchestrator publicationOrchestrator;
	private final LoadProviderRegistry loadProviderRegistry;

	public MetadataLoadManagementService(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		LoadProviderRegistry loadProviderRegistry,
		IndexerLifecycleEventBus eventBus,
		CommandService commandService
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.loadProviderRegistry = Objects.requireNonNull(loadProviderRegistry, "loadProviderRegistry");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		this.createIndexer = new CreateIndexerOperation(metadataRepository);
		this.commandService = Objects.requireNonNull(commandService, "commandService");
		this.publicationOrchestrator = new LoadPublicationOrchestrator(commandService);
	}

	@Override
	public Future<IndexerLoadRecord> create(CreateLoadRequest request) {
		Objects.requireNonNull(request, "request");
		return metadataRepository.ensureTarget(request.targetName(), null)
			.compose(target -> loadRepository.getActiveByTargetId(target.id())
				.compose(active -> active
					.map(load -> Future.<TargetRecord>failedFuture(
						"Active indexer load already exists for target: " + target.id()
					))
					.orElseGet(() -> Future.succeededFuture(target))))
			.compose(target -> createLoadIndexer(request, target)
				.compose(loadIndexer -> createLiveIndexer(request, target)
					.compose(liveIndexer -> insertLoad(request, target, loadIndexer, liveIndexer)
						.compose(ignored -> publishCreatedEvents(loadIndexer, liveIndexer))
						.compose(ignored -> commandService.submit(new StartLoadCommand(
							loadIndexer.id(),
							0L
						)))
						.compose(ignored -> load(loadIndexer.id())))));
	}

	@Override
	public Future<IndexerLoadRecord> recoverCreated(RecoverCreatedLoadRequest request) {
		Objects.requireNonNull(request, "request");
		return load(request.indexerId())
			.compose(load -> validateRecoverable(load, request))
			.compose(load -> commandService.submit(new StartLoadCommand(
				load.indexerId(),
				load.version()
			)))
			.compose(ignored -> load(request.indexerId()));
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

	private Future<IndexerRecord> createLoadIndexer(CreateLoadRequest request, TargetRecord target) {
		return createIndexer.create(new InsertIndexer(
			request.prefix(), target.id(), target.targetName(), request.indexName(), request.queueName(),
			IndexerType.INDEX, IndexerRole.LOAD_WRITER, IndexResourceOwnership.OWNER,
			IndexerRuntimeState.ACTIVE, PublicationState.UNPUBLISHED, MutationState.WRITABLE
		));
	}

	private Future<IndexerRecord> createLiveIndexer(CreateLoadRequest request, TargetRecord target) {
		if (request.liveWriterPolicy() != LiveWriterPolicy.CREATE_IMMEDIATELY) {
			return Future.succeededFuture();
		}
		String queueName = request.liveQueueName() == null
			? request.queueName() + "--live"
			: request.liveQueueName();
		return createIndexer.create(new InsertIndexer(
			request.prefix(), target.id(), target.targetName(), request.indexName(), queueName,
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

	private Future<IndexerLoadRecord> load(Integer indexerId) {
		return loadRepository.getByIndexerId(indexerId).compose(found -> found
			.map(Future::succeededFuture)
			.orElseGet(() -> Future.failedFuture("Indexer load not found: " + indexerId)));
	}
}
