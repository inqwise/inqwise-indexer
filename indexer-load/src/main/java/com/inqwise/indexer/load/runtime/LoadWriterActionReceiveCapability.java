package com.inqwise.indexer.load.runtime;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;
import com.inqwise.indexer.load.catalog.LazyLiveWriterCatalog;
import com.inqwise.indexer.load.events.LazyLiveWriterPreparationConflictEvent;
import com.inqwise.indexer.load.events.LazyLiveWriterPreparationConflictReason;
import com.inqwise.indexer.load.events.LoadEventChannels;
import com.inqwise.indexer.load.repository.AttachLiveWriterRequest;
import com.inqwise.indexer.load.repository.AttachLiveWriterResult;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.inqwise.coordination.ExclusiveFlowCoordinator;
import com.inqwise.coordination.LocalExclusiveFlowCoordinator;
import com.inqwise.events.EventEnvelope;
import com.inqwise.events.EventPublisher;
import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.cleanup.DeleteIndexerCommand;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.providers.ActionReceiveReadiness;
import com.inqwise.indexer.providers.IndexerActionReceiveCapability;
import com.inqwise.indexer.providers.PrepareIndexerForActionsRequest;
import com.inqwise.indexer.providers.PreparedIndexers;

import io.vertx.core.Future;

public class LoadWriterActionReceiveCapability implements IndexerActionReceiveCapability {
	private final LazyLiveWriterCatalog lazyLiveWriterCatalog;
	private final IndexerLoadRepository loadRepository;
	private final CommandService commandService;
	private final EventPublisher eventPublisher;
	private final ExclusiveFlowCoordinator flowCoordinator;

	public LoadWriterActionReceiveCapability(
		LazyLiveWriterCatalog lazyLiveWriterCatalog,
		IndexerLoadRepository loadRepository,
		CommandService commandService,
		EventPublisher eventPublisher,
		ExclusiveFlowCoordinator flowCoordinator
	) {
		this.lazyLiveWriterCatalog = Objects.requireNonNull(lazyLiveWriterCatalog, "lazyLiveWriterCatalog");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.commandService = commandService;
		this.eventPublisher = eventPublisher == null ? EventPublisher.NOOP : eventPublisher;
		this.flowCoordinator = Objects.requireNonNull(flowCoordinator, "flowCoordinator");
	}

	@Override
	public Future<ActionReceiveReadiness> canReceive(IndexerRecord indexer, IndexerActionItem action) {
		Objects.requireNonNull(indexer, "indexer");
		Objects.requireNonNull(action, "action");

		if (indexer.role() != IndexerRole.LOAD_WRITER || !isLiveAction(action)) {
			return Future.succeededFuture(ActionReceiveReadiness.NO);
		}

		return loadRepository.getByIndexerId(indexer.id())
			.map(found -> {
				if (found.isEmpty()) {
					return ActionReceiveReadiness.NO;
				}

				IndexerLoadRecord load = found.get();
				if (!isActive(load.state())
					|| load.liveWriterPolicy() != LiveWriterPolicy.CREATE_ON_FIRST_LIVE_ACTION
					|| load.liveIndexerId() != null) {
					return ActionReceiveReadiness.NO;
				}

				return ActionReceiveReadiness.REQUIRES_PREPARE;
			});
	}

	@Override
	public Future<PreparedIndexers> prepareToReceive(PrepareIndexerForActionsRequest request) {
		Objects.requireNonNull(request, "request");
		IndexerRecord loadIndexer = Objects.requireNonNull(request.indexer(), "indexer");

		return flowCoordinator.execute(
			lazyLiveWriterFlowKey(loadIndexer.id()),
			PreparedIndexers.class,
			promise -> loadRepository.getByIndexerId(loadIndexer.id())
				.compose(found -> found
					.map(Future::succeededFuture)
					.orElseGet(() -> Future.failedFuture(
						"Indexer load not found: " + loadIndexer.id()
					)))
				.compose(load -> prepare(load, loadIndexer, true, request.commandId()))
				.onComplete(promise)
		);
	}

	private Future<PreparedIndexers> prepare(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer,
		boolean retryStaleState,
		String correlationId
	) {
		if (load.liveIndexerId() != null) {
			return lazyLiveWriterCatalog.getLiveWriter(load.liveIndexerId())
				.map(indexer -> new PreparedIndexers(java.util.List.of(indexer), false));
		}

		if (!isActive(load.state())) {
			return Future.failedFuture("Indexer load is not active: " + load.state());
		}
		if (load.liveWriterPolicy() != LiveWriterPolicy.CREATE_ON_FIRST_LIVE_ACTION) {
			return Future.failedFuture("Indexer load does not allow lazy live writer: " + load.indexerId());
		}
		if (!load.targetId().equals(loadIndexer.targetId())) {
			return Future.failedFuture("Load target mismatch: " + load.indexerId());
		}

		return lazyLiveWriterCatalog.createAttachedLiveWriter(load, loadIndexer)
			.compose(liveWriter -> attachPreparedLiveWriter(
				load,
				loadIndexer,
				liveWriter,
				retryStaleState,
				correlationId
			));
	}

	private Future<PreparedIndexers> attachPreparedLiveWriter(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer,
		IndexerRecord liveWriter,
		boolean retryStaleState,
		String correlationId
	) {
		return loadRepository.attachLiveWriterIfAbsent(new AttachLiveWriterRequest(
			load.indexerId(),
			liveWriter.id(),
			load.version()
		)).compose(attached -> preparedLiveWriter(
			load,
			liveWriter,
			attached,
			correlationId
		)).recover(error -> recoverStaleState(
			load,
			loadIndexer,
			liveWriter,
			retryStaleState,
			correlationId,
			error
		));
	}

	private Future<PreparedIndexers> recoverStaleState(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer,
		IndexerRecord candidate,
		boolean retryStaleState,
		String correlationId,
		Throwable error
	) {
		if (!isVersionConflict(error)) {
			return Future.failedFuture(error);
		}

		if (!retryStaleState) {
			return abandonCandidate(
				load,
				candidate,
				null,
				LazyLiveWriterPreparationConflictReason.VERSION_CONFLICT,
				correlationId
			).compose(ignored -> Future.failedFuture(new RetryableStaleStateException(
				"Indexer load changed while preparing live writer: " + load.indexerId(),
				error
			)));
		}

		return loadRepository.getByIndexerId(load.indexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())))
			.compose(reloaded -> {
				LazyLiveWriterPreparationConflictReason reason = reloaded.liveIndexerId() != null
					? LazyLiveWriterPreparationConflictReason.ATTACH_LOST
					: isEligible(reloaded, loadIndexer)
						? LazyLiveWriterPreparationConflictReason.VERSION_CONFLICT
						: LazyLiveWriterPreparationConflictReason.LOAD_NOT_ELIGIBLE_AFTER_RELOAD;

				return abandonCandidate(
					reloaded,
					candidate,
					reloaded.liveIndexerId(),
					reason,
					correlationId
				).compose(ignored -> prepare(reloaded, loadIndexer, false, correlationId));
			});
	}

	private Future<PreparedIndexers> preparedLiveWriter(
		IndexerLoadRecord load,
		IndexerRecord candidate,
		AttachLiveWriterResult attached,
		String correlationId
	) {
		if (attached.liveIndexerId().equals(candidate.id())) {
			return Future.succeededFuture(new PreparedIndexers(java.util.List.of(candidate), true));
		}

		return abandonCandidate(
			load,
			candidate,
			attached.liveIndexerId(),
			LazyLiveWriterPreparationConflictReason.ATTACH_LOST,
			correlationId
		).compose(ignored -> lazyLiveWriterCatalog.getLiveWriter(attached.liveIndexerId())
			.map(winner -> new PreparedIndexers(java.util.List.of(winner), false)));
	}

	private Future<Void> abandonCandidate(
		IndexerLoadRecord load,
		IndexerRecord candidate,
		Integer winnerLiveIndexerId,
		LazyLiveWriterPreparationConflictReason reason,
		String correlationId
	) {
		if (commandService == null) {
			return publishConflict(
				load,
				candidate.id(),
				winnerLiveIndexerId,
				reason,
				false,
				null,
				correlationId
			);
		}

		return commandService.submit(new DeleteIndexerCommand(candidate.id(), candidate.version()))
			.compose(ignored -> publishConflict(
				load,
				candidate.id(),
				winnerLiveIndexerId,
				reason,
				true,
				null,
				correlationId
			))
			.recover(error -> publishConflict(
				load,
				candidate.id(),
				winnerLiveIndexerId,
				LazyLiveWriterPreparationConflictReason.CLEANUP_FAILED,
				false,
				false,
				correlationId
			));
	}

	private Future<Void> publishConflict(
		IndexerLoadRecord load,
		Integer candidateLiveIndexerId,
		Integer winnerLiveIndexerId,
		LazyLiveWriterPreparationConflictReason reason,
		boolean cleanupSubmitted,
		Boolean cleanupSucceeded,
		String correlationId
	) {
		Instant timestamp = Instant.now();
		LazyLiveWriterPreparationConflictEvent payload =
			new LazyLiveWriterPreparationConflictEvent(
				load.targetId(),
				load.indexerId(),
				candidateLiveIndexerId,
				winnerLiveIndexerId,
				reason,
				cleanupSubmitted,
				cleanupSucceeded,
				timestamp
			);
		EventEnvelope<LazyLiveWriterPreparationConflictEvent> envelope = new EventEnvelope<>(
			UUID.randomUUID().toString(),
			LoadEventChannels.LAZY_LIVE_WRITER_PREPARATION_CONFLICT_TYPE,
			timestamp,
			"indexer-load",
			correlationId,
			payload
		);

		return eventPublisher.publish(
			LoadEventChannels.LAZY_LIVE_WRITER_PREPARATION_CONFLICT,
			envelope
		).recover(error -> Future.succeededFuture());
	}

	private boolean isEligible(IndexerLoadRecord load, IndexerRecord loadIndexer) {
		return load.liveIndexerId() == null
			&& isActive(load.state())
			&& load.liveWriterPolicy() == LiveWriterPolicy.CREATE_ON_FIRST_LIVE_ACTION
			&& load.targetId().equals(loadIndexer.targetId());
	}

	private boolean isLiveAction(IndexerActionItem action) {
		return action.getActionType() == IndexerActionType.PUT_DOCUMENT
			|| action.getActionType() == IndexerActionType.REMOVE_DOCUMENT;
	}

	private boolean isActive(IndexerLoadState state) {
		return state != IndexerLoadState.PUBLISHED
			&& state != IndexerLoadState.FAILED
			&& state != IndexerLoadState.CANCELLED;
	}

	private boolean isVersionConflict(Throwable error) {
		return error.getMessage() != null && error.getMessage().contains("version conflict");
	}

	private String lazyLiveWriterFlowKey(Integer indexerId) {
		return "load-live-writer:" + indexerId;
	}
}
