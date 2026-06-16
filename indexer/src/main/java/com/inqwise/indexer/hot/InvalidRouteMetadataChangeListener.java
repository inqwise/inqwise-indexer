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
		return repository.getIndexerById(event.getIndexerId())
			.map(found -> {
				found.ifPresent(this::invalidate);
				return null;
			});
	}

	Future<Void> invalidate(TargetMetadataChanged event) {
		return repository.getTargetById(event.getTargetId())
			.map(found -> {
				found.ifPresent(this::invalidate);
				return null;
			});
	}

	private void invalidate(IndexerRecord indexer) {
		invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
			indexer.targetName(),
			null,
			null,
			null,
			null
		));
		invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
			null,
			null,
			indexer.targetId(),
			null,
			null
		));
		invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
			null,
			null,
			null,
			indexer.id(),
			null
		));
	}

	private void invalidate(TargetRecord target) {
		invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
			target.targetName(),
			null,
			null,
			null,
			null
		));
		invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
			null,
			null,
			target.id(),
			null,
			null
		));
	}
}
