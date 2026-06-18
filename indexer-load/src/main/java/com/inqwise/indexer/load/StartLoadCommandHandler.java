package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.TargetRecord;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class StartLoadCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final IndexerQueueClient queueClient;
	private final LoadProviderRegistry loadProviderRegistry;
	private final IndexerLifecycleEventBus eventBus;

	public StartLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		IndexerQueueClient queueClient,
		LoadProvider loadProvider,
		IndexerLifecycleEventBus eventBus
	) {
		this(
			metadataRepository,
			loadRepository,
			queueClient,
			new InMemoryLoadProviderRegistry().register("default", loadProvider),
			eventBus
		);
	}

	public StartLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		IndexerQueueClient queueClient,
		LoadProviderRegistry loadProviderRegistry,
		IndexerLifecycleEventBus eventBus
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.queueClient = Objects.requireNonNull(queueClient, "queueClient");
		this.loadProviderRegistry = Objects.requireNonNull(loadProviderRegistry, "loadProviderRegistry");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
	}

	@Override
	public String getType() {
		return StartLoadCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		StartLoadCommand start = new StartLoadCommand(command.toJson());

		return loadRepository.getByIndexerId(start.getIndexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + start.getIndexerId())))
			.compose(load -> start(load, start));
	}

	private Future<Void> start(IndexerLoadRecord load, StartLoadCommand start) {
		if (load.state() == IndexerLoadState.CREATED) {
			if (load.version() != start.getExpectedLoadVersion()) {
				return Future.failedFuture(
					"Indexer load version conflict for id " + load.indexerId() + ": expected "
						+ start.getExpectedLoadVersion() + " but was " + load.version()
				);
			}

			return loadRepository.updateState(new UpdateIndexerLoadState(
				load.indexerId(),
				IndexerLoadState.STARTING,
				load.version()
			)).compose(ignored -> loadRepository.getByIndexerId(load.indexerId()))
				.compose(updated -> updated
					.map(Future::succeededFuture)
					.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())))
				.compose(updated -> publishStateChanged(updated)
					.compose(ignored -> startProvider(updated)));
		}

		if (load.state() == IndexerLoadState.STARTING) {
			return startProvider(load);
		}

		if (load.state() == IndexerLoadState.FAILED
			|| load.state() == IndexerLoadState.CANCELLED
			|| load.state() == IndexerLoadState.PUBLISHED) {
			return Future.failedFuture("Indexer load is not startable: " + load.state());
		}

		return Future.succeededFuture();
	}

	private Future<Void> startProvider(IndexerLoadRecord load) {
		return metadataRepository.getIndexerById(load.indexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Load writer not found: " + load.indexerId())))
			.compose(loadWriter -> metadataRepository.getTargetById(load.targetId())
				.compose(found -> found
					.map(Future::succeededFuture)
					.orElseGet(() -> Future.failedFuture("Target not found: " + load.targetId())))
				.compose(target -> buildRequest(load, loadWriter, target)
					.compose(request -> loadProviderRegistry.get(load.providerId())
						.compose(provider -> provider.start(request, writer(load, loadWriter)))
						.compose(ignored -> markHistoricalLoading(load.indexerId()))
						.recover(error -> markProviderStartFailed(load.indexerId(), error)
							.compose(ignored -> Future.failedFuture(error))))));
	}

	private Future<LoadRequest> buildRequest(
		IndexerLoadRecord load,
		IndexerRecord loadWriter,
		TargetRecord target
	) {
		if (load.liveIndexerId() == null) {
			return Future.succeededFuture(loadRequest(load, loadWriter, target));
		}

		return metadataRepository.getIndexerById(load.liveIndexerId())
			.compose(found -> found
				.map(ignored -> Future.succeededFuture(loadRequest(load, loadWriter, target)))
				.orElseGet(() -> Future.failedFuture("Live writer not found: " + load.liveIndexerId())));
	}

	private LoadRequest loadRequest(
		IndexerLoadRecord load,
		IndexerRecord loadWriter,
		TargetRecord target
	) {
		return new LoadRequest(
			load.indexerId(),
			load.targetId(),
			load.liveIndexerId(),
			load.providerId(),
			target.targetName(),
			loadWriter.indexName(),
			loadWriter.queueName(),
			load.reloadStartAt(),
			load.liveReplayFrom(),
			load.sourceFrom(),
			load.sourceTo(),
			copy(load.sourceQuery()),
			load.sourcePlaybookId()
		);
	}

	private QueueLoadWriter writer(IndexerLoadRecord load, IndexerRecord loadWriter) {
		return new QueueLoadWriter(
			load.targetId(),
			load.indexerId(),
			loadWriter.indexName(),
			loadWriter.queueName(),
			queueClient,
			loadRepository
		);
	}

	private Future<Void> markHistoricalLoading(Integer indexerId) {
		return loadRepository.getByIndexerId(indexerId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + indexerId)))
			.compose(load -> {
				if (load.state() != IndexerLoadState.STARTING) {
					return Future.succeededFuture();
				}

				return loadRepository.updateState(new UpdateIndexerLoadState(
					load.indexerId(),
					IndexerLoadState.HISTORICAL_LOADING,
					load.version()
				)).compose(ignored -> loadRepository.getByIndexerId(load.indexerId()))
					.compose(updated -> updated
						.map(this::publishStateChanged)
						.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())));
			});
	}

	private Future<Void> markProviderStartFailed(Integer indexerId, Throwable error) {
		return loadRepository.getByIndexerId(indexerId)
			.compose(found -> found
				.map(load -> loadRepository.markFailed(new UpdateIndexerLoadFailure(
					indexerId,
					error == null || error.getMessage() == null
						? "Load provider failed to start"
						: error.getMessage(),
					null,
					load.version()
				)).compose(ignored -> loadRepository.getByIndexerId(indexerId))
					.compose(updated -> updated
						.map(this::publishStateChanged)
						.orElseGet(() -> Future.failedFuture("Indexer load not found: " + indexerId))))
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + indexerId)));
	}

	private Future<Void> publishStateChanged(IndexerLoadRecord load) {
		return eventBus.publish(new IndexerMetadataChanged(
			load.indexerId(),
			getType(),
			load.version()
		));
	}

	private JsonObject copy(JsonObject json) {
		return json == null ? null : json.copy();
	}
}
