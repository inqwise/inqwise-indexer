package com.inqwise.indexer.hot;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerLifecycleSubscription;
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
	private IndexerLifecycleSubscription indexerSubscription;
	private IndexerLifecycleSubscription targetSubscription;

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
		).compose(created -> {
			setIndexerSubscription(created);
			return eventBus.subscribeTarget(event ->
				invalidate(event).onFailure(error ->
					logger.error("Target metadata invalidation failed", error))
			);
		}).onSuccess(created -> {
			setTargetSubscription(created);
		}).map((Void) null).onFailure(ignored -> clearFailedStart());
		return startFuture;
	}

	public synchronized Future<Void> stop() {
		IndexerLifecycleSubscription closingTarget = targetSubscription;
		IndexerLifecycleSubscription closingIndexer = indexerSubscription;
		targetSubscription = null;
		indexerSubscription = null;
		startFuture = null;

		Future<Void> stopped = closingTarget == null
			? Future.succeededFuture()
			: closingTarget.close();
		return stopped.compose(ignored -> closingIndexer == null
			? Future.succeededFuture()
			: closingIndexer.close());
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
		if (targetSubscription != null) {
			targetSubscription.close();
			targetSubscription = null;
		}
		if (indexerSubscription != null) {
			indexerSubscription.close();
			indexerSubscription = null;
		}
		startFuture = null;
	}

	private synchronized void setIndexerSubscription(
		IndexerLifecycleSubscription created
	) {
		indexerSubscription = created;
	}

	private synchronized void setTargetSubscription(
		IndexerLifecycleSubscription created
	) {
		targetSubscription = created;
	}
}
