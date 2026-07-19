package com.inqwise.indexer.load.runtime;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;
import com.inqwise.indexer.load.repository.RequestIndexerLoadBarrier;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadBarrier;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadState;
import com.inqwise.indexer.load.commands.LoadPublicationOrchestrator;


import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.inqwise.indexer.actions.CatchUpBarrierActionItem;
import com.inqwise.indexer.actions.CompleteIndexActionItem;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.providers.IndexerMarkerHandler;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.routing.SubmitIndexActionsCommand;

import io.vertx.core.Future;

public class LoadIndexerMarkerHandler implements IndexerMarkerHandler {
	public static final String COMPLETE_COMMAND_TYPE = "indexer.load.complete-marker";
	public static final String BARRIER_REQUEST_COMMAND_TYPE = "indexer.load.catch-up-barrier-request";
	public static final String BARRIER_COMMAND_TYPE = "indexer.load.catch-up-barrier-marker";

	private final IndexerLoadRepository loadRepository;
	private final IndexerLifecycleEventBus eventBus;
	private final CommandService commandService;
	private final LoadPublicationOrchestrator publicationOrchestrator;

	public LoadIndexerMarkerHandler(
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus,
		CommandService commandService
	) {
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		this.commandService = Objects.requireNonNull(commandService, "commandService");
		this.publicationOrchestrator = new LoadPublicationOrchestrator(commandService);
	}

	@Override
	public Future<Void> complete(IndexerModel model, CompleteIndexActionItem item) {
		return loadRepository.getByIndexerId(model.getId())
			.compose(found -> found
				.map(this::markHistoricalComplete)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + model.getId())));
	}

	@Override
	public Future<Void> catchUpBarrier(IndexerModel model, CatchUpBarrierActionItem item) {
		return loadRepository.getActiveByTargetIndexerId(model.getId())
			.compose(found -> found
				.map(load -> markBarrierReached(model, load, item))
				.orElseGet(Future::succeededFuture));
	}

	private Future<Void> markHistoricalComplete(IndexerLoadRecord load) {
		if (load.state() == IndexerLoadState.CATCH_UP_READY
			|| load.state() == IndexerLoadState.WAITING_FOR_REVIEW
			|| load.state() == IndexerLoadState.APPROVED
			|| load.state() == IndexerLoadState.PUBLISHED) {
			return Future.succeededFuture();
		}

		if (load.state() == IndexerLoadState.FAILED || load.state() == IndexerLoadState.CANCELLED) {
			return Future.failedFuture("Indexer load is not completable: " + load.state());
		}
		if (load.state() == IndexerLoadState.HISTORICAL_COMPLETE
			|| load.state() == IndexerLoadState.CATCH_UP_BARRIER_REQUESTED) {
			return advanceAfterHistoricalComplete(load);
		}

		return loadRepository.updateState(UpdateIndexerLoadState.builder()
			.withIndexerId(load.indexerId())
			.withState(IndexerLoadState.HISTORICAL_COMPLETE)
			.withExpectedVersion(load.version())
			.build()).onSuccess(ignored -> publishWakeUp(
			load,
			COMPLETE_COMMAND_TYPE,
			load.version() + 1
		)).compose(ignored -> loadRepository.getByIndexerId(load.indexerId()))
			.compose(updated -> updated
				.map(this::advanceAfterHistoricalComplete)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())));
	}

	private Future<Void> advanceAfterHistoricalComplete(IndexerLoadRecord load) {
		if (load.liveIndexerId() != null) {
			return requestCatchUpBarrier(load);
		}
		if (load.reviewRequired() && load.state() == IndexerLoadState.HISTORICAL_COMPLETE) {
			return loadRepository.updateState(UpdateIndexerLoadState.builder()
				.withIndexerId(load.indexerId())
				.withState(IndexerLoadState.WAITING_FOR_REVIEW)
				.withExpectedVersion(load.version())
				.build()).onSuccess(ignored -> publishWakeUp(
				load,
				COMPLETE_COMMAND_TYPE,
				load.version() + 1
			));
		}
		return publishIfReady(load);
	}

	private Future<Void> requestCatchUpBarrier(IndexerLoadRecord load) {
		if (load.state() == IndexerLoadState.CATCH_UP_BARRIER_REQUESTED) {
			return submitCatchUpBarrier(load);
		}

		Instant barrierTimestamp = Instant.now();
		return loadRepository.requestBarrier(RequestIndexerLoadBarrier.builder()
			.withIndexerId(load.indexerId())
			.withBarrierId(UUID.randomUUID().toString())
			.withBarrierTimestamp(barrierTimestamp)
			.withExpectedVersion(load.version())
			.build()).compose(ignored -> loadRepository.getByIndexerId(load.indexerId()))
			.compose(updated -> updated
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())))
			.compose(updated -> {
				publishWakeUp(updated, BARRIER_REQUEST_COMMAND_TYPE, updated.version());
				return submitCatchUpBarrier(updated);
			});
	}

	private Future<Void> submitCatchUpBarrier(IndexerLoadRecord load) {
		CatchUpBarrierActionItem barrier = CatchUpBarrierActionItem.builder()
			.withTargetId(load.targetId())
			.withIndexerId(load.liveIndexerId())
			.withBarrierId(load.lastBarrierId())
			.withBarrierTimestamp(load.lastBarrierTimestamp())
			.build();
		return commandService.submit(SubmitIndexActionsCommand.builder()
			.withActions(List.of(barrier))
			.build());
	}

	private Future<Void> markBarrierReached(
		IndexerModel model,
		IndexerLoadRecord load,
		CatchUpBarrierActionItem item
	) {
		if (!model.getId().equals(load.liveIndexerId())) {
			return Future.failedFuture("Catch-up barrier indexer is not linked to active load: " + model.getId());
		}

		if (load.state() == IndexerLoadState.CATCH_UP_READY
			|| load.state() == IndexerLoadState.WAITING_FOR_REVIEW
			|| load.state() == IndexerLoadState.APPROVED) {
			return Objects.equals(item.getBarrierId(), load.lastBarrierId())
				? publishIfReady(load)
				: Future.failedFuture("Catch-up barrier does not match active load: " + load.indexerId());
		}
		if (load.state() != IndexerLoadState.CATCH_UP_BARRIER_REQUESTED) {
			return Future.failedFuture("Indexer load is not waiting for catch-up barrier: " + load.state());
		}

		return loadRepository.markBarrierReached(UpdateIndexerLoadBarrier.builder()
			.withIndexerId(load.indexerId())
			.withBarrierId(item.getBarrierId())
			.withBarrierTimestamp(item.getBarrierTimestamp())
			.withReachedAt(Instant.now())
			.withExpectedVersion(load.version())
			.build()).onSuccess(ignored -> publishWakeUp(
			load,
			BARRIER_COMMAND_TYPE,
			load.version() + 1
		)).compose(ignored -> loadRepository.getByIndexerId(load.indexerId()))
			.compose(updated -> updated
				.map(this::publishIfReady)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())));
	}

	private Future<Void> publishIfReady(IndexerLoadRecord load) {
		return publicationOrchestrator.publishIfReady(load);
	}

	private void publishWakeUp(
		IndexerLoadRecord load,
		String commandType,
		long version
	) {
		eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
			load.indexerId(),
			load.targetId(),
			commandType,
			version
		));
	}
}
