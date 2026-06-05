package com.inqwise.indexer.load;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.CatchUpBarrierActionItem;
import com.inqwise.indexer.CompleteIndexActionItem;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMarkerHandler;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerModel;
import com.inqwise.indexer.commands.CommandService;

import io.vertx.core.Future;

public class LoadIndexerMarkerHandler implements IndexerMarkerHandler {
	public static final String COMPLETE_COMMAND_TYPE = "indexer.load.complete-marker";
	public static final String BARRIER_COMMAND_TYPE = "indexer.load.catch-up-barrier-marker";

	private final IndexerLoadRepository loadRepository;
	private final IndexerLifecycleEventBus eventBus;
	private final LoadPublicationOrchestrator publicationOrchestrator;

	public LoadIndexerMarkerHandler(
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus
	) {
		this(loadRepository, eventBus, null);
	}

	public LoadIndexerMarkerHandler(
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus,
		CommandService commandService
	) {
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		this.publicationOrchestrator = commandService == null
			? null
			: new LoadPublicationOrchestrator(commandService);
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
				.orElseGet(() -> Future.failedFuture("Active indexer load not found for indexer: " + model.getId())));
	}

	private Future<Void> markHistoricalComplete(IndexerLoadRecord load) {
		if (load.state() == IndexerLoadState.HISTORICAL_COMPLETE
			|| load.state() == IndexerLoadState.CATCH_UP_BARRIER_REQUESTED
			|| load.state() == IndexerLoadState.CATCH_UP_READY
			|| load.state() == IndexerLoadState.WAITING_FOR_REVIEW
			|| load.state() == IndexerLoadState.APPROVED
			|| load.state() == IndexerLoadState.PUBLISHING
			|| load.state() == IndexerLoadState.PUBLISHED) {
			return Future.succeededFuture();
		}

		if (load.state() == IndexerLoadState.FAILED || load.state() == IndexerLoadState.CANCELLED) {
			return Future.failedFuture("Indexer load is not completable: " + load.state());
		}

		return loadRepository.updateState(new UpdateIndexerLoadState(
			load.indexerId(),
			IndexerLoadState.HISTORICAL_COMPLETE,
			load.version()
		)).compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
			load.indexerId(),
			COMPLETE_COMMAND_TYPE,
			load.version() + 1
		))).compose(ignored -> loadRepository.getByIndexerId(load.indexerId()))
			.compose(updated -> updated
				.map(this::publishIfReady)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())));
	}

	private Future<Void> markBarrierReached(
		IndexerModel model,
		IndexerLoadRecord load,
		CatchUpBarrierActionItem item
	) {
		if (!model.getId().equals(load.liveIndexerId())) {
			return Future.failedFuture("Catch-up barrier indexer is not linked to active load: " + model.getId());
		}

		if (load.state() != IndexerLoadState.HISTORICAL_COMPLETE
			&& load.state() != IndexerLoadState.CATCH_UP_BARRIER_REQUESTED
			&& load.state() != IndexerLoadState.CATCH_UP_READY
			&& load.state() != IndexerLoadState.WAITING_FOR_REVIEW
			&& load.state() != IndexerLoadState.APPROVED) {
			return Future.failedFuture("Indexer load is not ready for catch-up barrier: " + load.state());
		}

		if (item.getBarrierId() != null && item.getBarrierId().equals(load.lastBarrierId())) {
			return Future.succeededFuture();
		}

		return loadRepository.markBarrierReached(new UpdateIndexerLoadBarrier(
			load.indexerId(),
			item.getBarrierId(),
			item.getBarrierTimestamp(),
			Instant.now(),
			load.version()
		)).compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
			load.indexerId(),
			BARRIER_COMMAND_TYPE,
			load.version() + 1
		))).compose(ignored -> loadRepository.getByIndexerId(load.indexerId()))
			.compose(updated -> updated
				.map(this::publishIfReady)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())));
	}

	private Future<Void> publishIfReady(IndexerLoadRecord load) {
		if (publicationOrchestrator == null) {
			return Future.succeededFuture();
		}
		return publicationOrchestrator.publishIfReady(load);
	}
}
