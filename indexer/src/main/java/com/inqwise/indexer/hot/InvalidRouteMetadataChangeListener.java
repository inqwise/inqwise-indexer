package com.inqwise.indexer.hot;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerLifecycleSubscription;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.TargetMetadataChanged;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.TargetRecord;

import io.vertx.core.Future;

public class InvalidRouteMetadataChangeListener {
	private static final Logger logger =
		LogManager.getLogger(InvalidRouteMetadataChangeListener.class);

	private final DocumentStoreMetadataRepository repository;
	private final IndexerLifecycleEventBus eventBus;
	private final InvalidRouteCache invalidRouteCache;
	private Future<Void> startFuture;
	private IndexerLifecycleSubscription indexerSubscription;
	private IndexerLifecycleSubscription targetSubscription;

	public InvalidRouteMetadataChangeListener(
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus,
		InvalidRouteCache invalidRouteCache
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		this.invalidRouteCache = Objects.requireNonNull(invalidRouteCache, "invalidRouteCache");
	}

	public synchronized Future<Void> start() {
		if (startFuture != null) {
			return startFuture;
		}

		startFuture = eventBus.subscribe(event ->
			invalidate(event).onFailure(error ->
				logger.error("Indexer invalid-route invalidation failed", error))
		).compose(created -> {
			setIndexerSubscription(created);
			return eventBus.subscribeTarget(event ->
				invalidate(event).onFailure(error ->
					logger.error("Target invalid-route invalidation failed", error))
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
		invalidateDirectIndexerRoutes(event.getTargetId(), event.getIndexerId());
		return repository.getIndexerById(event.getIndexerId())
			.compose(found -> found
				.map(this::invalidateTargetEnvelope)
				.orElseGet(Future::succeededFuture));
	}

	Future<Void> invalidate(TargetMetadataChanged event) {
		invalidate(
			event.getTargetId(),
			event.getTargetName(),
			event.getPeriodKey()
		);
		return Future.succeededFuture();
	}

	private Future<Void> invalidateTargetEnvelope(IndexerRecord indexer) {
		return repository.getTargetById(indexer.targetId())
			.map(found -> {
				invalidateTargetEnvelope(
					indexer.targetName(),
					found.map(TargetRecord::periodKey).orElse(null)
				);
				return null;
			});
	}

	private void invalidateDirectIndexerRoutes(Integer targetId, Integer indexerId) {
		invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
			null,
			null,
			targetId,
			null,
			null
		));
		invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
			null,
			null,
			null,
			indexerId,
			null
		));
	}

	private void invalidate(TargetRecord target) {
		invalidate(target.id(), target.targetName(), target.periodKey());
	}

	private void invalidate(Integer targetId, String targetName, String periodKey) {
		invalidateTargetEnvelope(targetName, periodKey);
		invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
			null,
			null,
			targetId,
			null,
			null
		));
	}

	private void invalidateTargetEnvelope(String targetName, String periodKey) {
		invalidRouteCache.invalidateMatching(InvalidRouteInvalidation.exactPeriodKey(
			targetName,
			null,
			null,
			null,
			null
		));

		if (periodKey != null) {
			invalidRouteCache.invalidateMatching(InvalidRouteInvalidation.exactPeriodKey(
				targetName,
				periodKey,
				null,
				null,
				null
			));
		}
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
