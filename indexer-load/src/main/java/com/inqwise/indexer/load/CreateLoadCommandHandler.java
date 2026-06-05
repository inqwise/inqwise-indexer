package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.provisioning.CreateIndexerOperation;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class CreateLoadCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final IndexerQueueClient queueClient;
	private final LoadProviderRegistry loadProviderRegistry;
	private final IndexerLifecycleEventBus eventBus;
	private final CreateIndexerOperation createIndexer;

	public CreateLoadCommandHandler(
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

	public CreateLoadCommandHandler(
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
		this.createIndexer = new CreateIndexerOperation(metadataRepository);
	}

	@Override
	public String getType() {
		return CreateLoadCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CreateLoadCommand create = new CreateLoadCommand(command.toJson());

		return metadataRepository.ensureTarget(create.getTargetName(), null)
			.compose(target -> loadRepository.getActiveByTargetId(target.id())
				.compose(active -> active
					.map(load -> Future.<TargetRecord>failedFuture(
						"Active indexer load already exists for target: " + target.id()
					))
					.orElseGet(() -> Future.succeededFuture(target))))
			.compose(target -> createLoadIndexer(create, target)
				.compose(loadIndexer -> createLiveIndexer(create, target)
					.compose(liveIndexer -> insertLoad(create, target, loadIndexer, liveIndexer)
						.compose(ignored -> publishCreatedEvents(loadIndexer, liveIndexer))
						.compose(ignored -> startProvider(create, target, loadIndexer, liveIndexer)))));
	}

	private Future<IndexerRecord> createLoadIndexer(CreateLoadCommand create, TargetRecord target) {
		return createIndexer.create(new InsertIndexer(
			create.getPrefix(),
			target.id(),
			target.targetName(),
			create.getIndexName(),
			create.getQueueName(),
			IndexerType.INDEX,
			IndexerRole.LOAD_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerRuntimeState.ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		));
	}

	private Future<IndexerRecord> createLiveIndexer(CreateLoadCommand create, TargetRecord target) {
		if (create.getLiveWriterPolicy() != LiveWriterPolicy.CREATE_IMMEDIATELY) {
			return Future.succeededFuture();
		}

		return createIndexer.create(new InsertIndexer(
			create.getPrefix(),
			target.id(),
			target.targetName(),
			create.getIndexName(),
			liveQueueName(create),
			IndexerType.INDEX,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.ATTACHED,
			IndexerRuntimeState.ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		));
	}

	private Future<Void> insertLoad(
		CreateLoadCommand create,
		TargetRecord target,
		IndexerRecord loadIndexer,
		IndexerRecord liveIndexer
	) {
		return loadRepository.insert(new InsertIndexerLoad(
			loadIndexer.id(),
			target.id(),
			liveIndexer == null ? null : liveIndexer.id(),
			create.getProviderId(),
			IndexerLoadState.HISTORICAL_LOADING,
			create.getReloadStartAt(),
			create.getLiveReplayFrom(),
			create.getSourceFrom(),
			create.getSourceTo(),
			create.getSourceQuery(),
			create.getSourcePlaybookId(),
			create.isReviewRequired()
		));
	}

	private Future<Void> publishCreatedEvents(IndexerRecord loadIndexer, IndexerRecord liveIndexer) {
		Future<Void> published = eventBus.publish(new IndexerMetadataChanged(
			loadIndexer.id(),
			getType(),
			loadIndexer.version()
		));

		if (liveIndexer != null) {
			published = published.compose(ignored -> eventBus.publish(new IndexerMetadataChanged(
				liveIndexer.id(),
				getType(),
				liveIndexer.version()
			)));
		}

		return published;
	}

	private Future<Void> startProvider(
		CreateLoadCommand create,
		TargetRecord target,
		IndexerRecord loadIndexer,
		IndexerRecord liveIndexer
	) {
		QueueLoadWriter writer = new QueueLoadWriter(
			target.id(),
			loadIndexer.id(),
			loadIndexer.queueName(),
			queueClient,
			loadRepository
		);
		LoadRequest request = new LoadRequest(
			loadIndexer.id(),
			target.id(),
			liveIndexer == null ? null : liveIndexer.id(),
			create.getProviderId(),
			target.targetName(),
			loadIndexer.indexName(),
			loadIndexer.queueName(),
			create.getReloadStartAt(),
			create.getLiveReplayFrom(),
			create.getSourceFrom(),
			create.getSourceTo(),
			copy(create.getSourceQuery()),
			create.getSourcePlaybookId()
		);

		return loadProviderRegistry.get(create.getProviderId())
			.compose(provider -> provider.start(request, writer))
			.recover(error -> markProviderStartFailed(loadIndexer.id(), error)
				.compose(ignored -> Future.failedFuture(error)));
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
				)))
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + indexerId)));
	}

	private String liveQueueName(CreateLoadCommand create) {
		return create.getLiveQueueName() == null
			? create.getQueueName() + "--live"
			: create.getLiveQueueName();
	}

	private JsonObject copy(JsonObject json) {
		return json == null ? null : json.copy();
	}
}
