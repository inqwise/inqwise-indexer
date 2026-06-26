package com.inqwise.indexer.hot;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.TargetMetadataChanged;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.TargetRecord;

import io.vertx.core.Future;

public class InvalidRouteMetadataChangeListener {
	private final DocumentStoreMetadataRepository repository;
	private final IndexerLifecycleEventBus eventBus;
	private final InvalidRouteCache invalidRouteCache;

	public InvalidRouteMetadataChangeListener(
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus,
		InvalidRouteCache invalidRouteCache
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		this.invalidRouteCache = Objects.requireNonNull(invalidRouteCache, "invalidRouteCache");
	}

	public Future<Void> start() {
		return eventBus.subscribe(event ->
			invalidate(event).onFailure(Throwable::printStackTrace)
		).compose(ignored -> eventBus.subscribeTarget(event ->
			invalidate(event).onFailure(Throwable::printStackTrace)
		));
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
}
