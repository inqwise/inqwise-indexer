package com.inqwise.indexer.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.documents.IndexerDocumentStore;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.MetadataIndexerModels;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.providers.IndexerMarkerHandler;
import com.inqwise.indexer.providers.IndexerPlugins;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class IndexerRuntime {
	private final Function<IndexerRecord, Indexer> indexerFactory;
	private final Map<Integer, RuntimeEntry> indexersById = new ConcurrentHashMap<>();

	public IndexerRuntime(
		Function<IndexerRecord, Indexer> indexerFactory
	) {
		this.indexerFactory = Objects.requireNonNull(indexerFactory, "indexerFactory");
	}

	public IndexerRuntime(
		Vertx vertx,
		IndexerQueueClient queue,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerEventPublisher eventPublisher
	) {
		this(
			vertx,
			queue,
			documentStore,
			options,
			eventPublisher,
			IndexerPlugins.empty()
		);
	}

	public IndexerRuntime(
		Vertx vertx,
		IndexerQueueClient queue,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerEventPublisher eventPublisher,
		IndexerPlugins plugins
	) {
		this(indexer -> createVerticleBackedIndexer(
				vertx,
				toModel(indexer),
				queue,
				documentStore,
				options,
				eventPublisher,
				markerHandler(plugins, indexer)
			)
		);
	}

	private static IndexerMarkerHandler markerHandler(
		IndexerPlugins plugins,
		IndexerRecord indexer
	) {
		return (plugins == null ? IndexerPlugins.empty() : plugins)
			.markerHandler(toModel(indexer));
	}

	public Future<Void> reconcile(IndexerRecord indexer) {
		if (indexer.status() != IndexerStatus.AVAILABLE
			|| indexer.mutationState() == MutationState.DELETING) {
			return close(indexer.id());
		}

		if (indexer.provisioningState() == IndexerProvisioningState.READY
			&& indexer.runtimeState() == IndexerRuntimeState.ACTIVE) {
			return activate(indexer);
		}

		return close(indexer.id());
	}

	protected Future<Void> activate(IndexerRecord indexerRecord) {
		IndexerModel model = toModel(indexerRecord);
		RuntimeEntry existing = indexersById.get(indexerRecord.id());
		if (existing != null && sameRuntimeModel(existing.model(), model)) {
			return Future.succeededFuture();
		}

		if (existing != null) {
			indexersById.remove(indexerRecord.id(), existing);
		}
		Future<Void> closeExisting = existing == null
			? Future.succeededFuture()
			: existing.indexer().close();

		return closeExisting.compose(ignored -> {
			Indexer candidate = indexerFactory.apply(indexerRecord);
			return candidate.activate()
				.onSuccess(value -> indexersById.put(
					indexerRecord.id(),
					new RuntimeEntry(model, candidate)
				))
				.recover(error -> candidate.close()
					.recover(closeError -> {
						error.addSuppressed(closeError);
						return Future.succeededFuture();
					})
					.compose(value -> Future.failedFuture(error)));
		});
	}

	protected Future<Void> unregister(Integer indexerId) {
		RuntimeEntry entry = indexersById.remove(indexerId);
		return entry == null ? Future.succeededFuture() : entry.indexer().unregister();
	}

	public Future<Void> close(Integer indexerId) {
		RuntimeEntry entry = indexersById.remove(indexerId);
		return entry == null ? Future.succeededFuture() : entry.indexer().close();
	}

	public Future<Void> stop() {
		return closeAll();
	}

	private Future<Void> closeAll() {
		Future<Void> stopped = Future.succeededFuture();
		for (Integer indexerId : List.copyOf(indexersById.keySet())) {
			stopped = stopped.compose(ignored -> close(indexerId));
		}

		return stopped;
	}

	public Set<Integer> indexerIds() {
		return Set.copyOf(indexersById.keySet());
	}

	public List<IndexerSnapshot> snapshots() {
		return indexersById.values().stream()
			.map(entry -> entry.indexer().status())
			.toList();
	}

	public static IndexerModel toModel(IndexerRecord indexer) {
		return MetadataIndexerModels.fromRecord(indexer);
	}

	private static Indexer createVerticleBackedIndexer(
		Vertx vertx,
		IndexerModel model,
		IndexerQueueClient queue,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerEventPublisher eventPublisher,
		IndexerMarkerHandler markerHandler
	) {
		IndexerOptions resolvedOptions = options == null
			? IndexerOptions.builder().build()
			: options;
		IndexerEventPublisher resolvedPublisher = eventPublisher == null
			? IndexerEventPublisher.NOOP
			: eventPublisher;

		return new Indexer(
			vertx,
			model,
			queue,
			documentStore,
			resolvedOptions,
			resolvedPublisher,
			(processorModel, processorOptions, processHandler, processorEventPublisher) ->
				new VerticleIndexerProcessor(
					vertx,
					() -> new IndexerProcessorVerticle(
						processorModel,
						processorOptions,
						queue,
						processHandler,
						processorEventPublisher
					)
				),
			markerHandler
		);
	}

	private boolean sameRuntimeModel(IndexerModel current, IndexerModel next) {
		return Objects.equals(current.getQueueName(), next.getQueueName())
			&& current.getVersion() == next.getVersion();
	}

	private record RuntimeEntry(IndexerModel model, Indexer indexer) {
	}
}
