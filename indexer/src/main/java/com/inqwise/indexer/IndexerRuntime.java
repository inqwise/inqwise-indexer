package com.inqwise.indexer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.vertx.core.Future;

public class IndexerRuntime {
	private final IndexerRepository repository;
	private final IndexerLifecycleEventBus lifecycleEventBus;
	private final Function<IndexerModel, Indexer> indexerFactory;
	private final Map<Integer, Indexer> indexersById = new ConcurrentHashMap<>();

	public IndexerRuntime(
		IndexerRepository repository,
		IndexerLifecycleEventBus lifecycleEventBus,
		Function<IndexerModel, Indexer> indexerFactory
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.lifecycleEventBus = Objects.requireNonNull(lifecycleEventBus, "lifecycleEventBus");
		this.indexerFactory = Objects.requireNonNull(indexerFactory, "indexerFactory");
	}

	public Future<Void> start() {
		return lifecycleEventBus.subscribe(event ->
			reconcile(event).onFailure(Throwable::printStackTrace)
		);
	}

	public Future<Void> reconcile(Integer indexerId) {
		return repository.get(indexerId)
			.compose(found -> {
				if (found.isEmpty()) {
					return close(indexerId);
				}

				IndexerModel model = found.get();
				if (model.getStatus().isActive()) {
					return activate(model);
				}

				return Future.succeededFuture();
			});
	}

	public Future<Void> reconcile(IndexerLifecycleChanged event) {
		return repository.get(event.getIndexerId())
			.compose(found -> {
				if (found.isEmpty()) {
					return close(event.getIndexerId());
				}

				IndexerModel model = found.get();
				if (model.getStatus().isActive()) {
					return activate(model);
				}

				return Future.succeededFuture();
			});
	}

	protected Future<Void> activate(IndexerModel model) {
		Indexer indexer = indexersById.computeIfAbsent(
			model.getId(),
			ignored -> indexerFactory.apply(model)
		);

		return indexer.activate();
	}

	protected Future<Void> unregister(Integer indexerId) {
		Indexer indexer = indexersById.remove(indexerId);
		return indexer == null ? Future.succeededFuture() : indexer.unregister();
	}

	protected Future<Void> close(Integer indexerId) {
		Indexer indexer = indexersById.remove(indexerId);
		return indexer == null ? Future.succeededFuture() : indexer.close();
	}
}
