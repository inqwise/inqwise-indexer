package com.inqwise.indexer;

import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.providers.IndexerPlugins;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class IndexerRuntime {
	private static final Logger logger = LogManager.getLogger(IndexerRuntime.class);

	private final DocumentStoreMetadataRepository repository;
	private final IndexerLifecycleEventBus lifecycleEventBus;
	private final Function<IndexerRecord, Indexer> indexerFactory;
	private final Map<Integer, RuntimeEntry> indexersById = new ConcurrentHashMap<>();

	public IndexerRuntime(
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus lifecycleEventBus,
		Function<IndexerRecord, Indexer> indexerFactory
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.lifecycleEventBus = Objects.requireNonNull(lifecycleEventBus, "lifecycleEventBus");
		this.indexerFactory = Objects.requireNonNull(indexerFactory, "indexerFactory");
	}

	public IndexerRuntime(
		Vertx vertx,
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus lifecycleEventBus,
		IndexerQueueClient queue,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerEventPublisher eventPublisher
	) {
		this(
			vertx,
			repository,
			lifecycleEventBus,
			queue,
			documentStore,
			options,
			eventPublisher,
			IndexerPlugins.empty()
		);
	}

	public IndexerRuntime(
		Vertx vertx,
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus lifecycleEventBus,
		IndexerQueueClient queue,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerEventPublisher eventPublisher,
		IndexerPlugins plugins
	) {
		this(
			repository,
			lifecycleEventBus,
			indexer -> createVerticleBackedIndexer(
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

	public Future<Void> start() {
		return lifecycleEventBus.subscribe(event ->
			reconcile(event).onFailure(error -> logger.error(
				"Indexer runtime reconciliation failed for indexer {} under target {}",
				event.getIndexerId(),
				event.getTargetId(),
				error
			))
		);
	}

	public Future<Void> reconcile(Integer indexerId) {
		return repository.getIndexerById(indexerId)
			.compose(found -> {
				if (found.isEmpty()) {
					return close(indexerId);
				}

				return reconcile(found.get());
			});
	}

	public Future<Void> reconcile(IndexerMetadataChanged event) {
		return repository.getIndexerById(event.getIndexerId())
			.compose(found -> {
				if (found.isEmpty()) {
					return close(event.getIndexerId());
				}

				return reconcile(found.get());
			});
	}

	private Future<Void> reconcile(IndexerRecord indexer) {
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
			return existing.indexer().activate();
		}

		Future<Void> closeExisting = existing == null
			? Future.succeededFuture()
			: existing.indexer().close();

		return closeExisting.compose(ignored -> {
			Indexer indexer = indexerFactory.apply(indexerRecord);
			indexersById.put(indexerRecord.id(), new RuntimeEntry(model, indexer));
			return indexer.activate();
		});
	}

	protected Future<Void> unregister(Integer indexerId) {
		RuntimeEntry entry = indexersById.remove(indexerId);
		return entry == null ? Future.succeededFuture() : entry.indexer().unregister();
	}

	protected Future<Void> close(Integer indexerId) {
		RuntimeEntry entry = indexersById.remove(indexerId);
		return entry == null ? Future.succeededFuture() : entry.indexer().close();
	}

	public Future<Void> stop() {
		Future<Void> stopped = Future.succeededFuture();
		for (Integer indexerId : List.copyOf(indexersById.keySet())) {
			stopped = stopped.compose(ignored -> close(indexerId));
		}

		return stopped;
	}

	public List<IndexerSnapshot> snapshots() {
		return indexersById.values().stream()
			.map(entry -> entry.indexer().status())
			.toList();
	}

	public static IndexerModel toModel(IndexerRecord indexer) {
		return IndexerModel.builder()
			.withId(indexer.id())
			.withUid(indexer.uid())
			.withTargetId(indexer.targetId())
			.withTargetName(indexer.targetName())
			.withIndexName(indexer.indexName())
			.withQueueName(indexer.queueName())
			.withType(indexer.type())
			.withRole(indexer.role())
			.withIndexOwnership(indexer.indexOwnership())
			.withRuntimeState(indexer.runtimeState())
			.withVersion(indexer.version())
			.build();
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
		IndexerOptions resolvedOptions = options == null ? new IndexerOptions() : options;
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
