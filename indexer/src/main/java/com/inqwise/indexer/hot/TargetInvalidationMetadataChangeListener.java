package com.inqwise.indexer.hot;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.TargetMetadataChanged;

import io.vertx.core.Future;

public class TargetInvalidationMetadataChangeListener {
	private static final Logger logger =
		LogManager.getLogger(TargetInvalidationMetadataChangeListener.class);

	private final IndexerLifecycleEventBus eventBus;
	private final HotMetadataView hotMetadataView;
	private final TargetInvalidationRegistry registry;
	private Future<Void> startFuture;

	public TargetInvalidationMetadataChangeListener(
		IndexerLifecycleEventBus eventBus,
		HotMetadataView hotMetadataView,
		TargetInvalidationRegistry registry
	) {
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		this.hotMetadataView = Objects.requireNonNull(hotMetadataView, "hotMetadataView");
		this.registry = Objects.requireNonNull(registry, "registry");
	}

	public synchronized Future<Void> start() {
		if (startFuture != null) {
			return startFuture;
		}

		startFuture = eventBus.subscribe(event ->
			invalidate(event).onFailure(error ->
				logger.error("Indexer metadata invalidation failed", error))
		).compose(ignored -> eventBus.subscribeTarget(event ->
			invalidate(event).onFailure(error ->
				logger.error("Target metadata invalidation failed", error))
		)).onFailure(ignored -> clearFailedStart());
		return startFuture;
	}

	Future<Void> invalidate(IndexerMetadataChanged event) {
		hotMetadataView.invalidateHotTargetByIndexerId(event.getIndexerId());
		return registry.markInvalidated(event.getTargetId());
	}

	Future<Void> invalidate(TargetMetadataChanged event) {
		hotMetadataView.invalidateHotTargetByConcreteTargetId(event.getTargetId());
		return registry.markInvalidated(event.getTargetId());
	}

	private synchronized void clearFailedStart() {
		startFuture = null;
	}
}
