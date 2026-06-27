package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.provisioning.CreateIndexerOperation;

import io.vertx.core.Future;

public class CreateLoadCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final IndexerLifecycleEventBus eventBus;
	private final CreateIndexerOperation createIndexer;
	private final CommandService commandService;

	public CreateLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus
	) {
		this(
			metadataRepository,
			loadRepository,
			eventBus,
			null
		);
	}

	public CreateLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus,
		CommandService commandService
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		this.createIndexer = new CreateIndexerOperation(metadataRepository);
		this.commandService = commandService;
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
						.compose(ignored -> startLoad(loadIndexer)))));
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
			create.getLiveWriterPolicy(),
			create.getProviderId(),
			IndexerLoadState.CREATED,
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
		eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
			loadIndexer.id(),
			loadIndexer.targetId(),
			getType(),
			loadIndexer.version()
		));

		if (liveIndexer != null) {
			eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
				liveIndexer.id(),
				liveIndexer.targetId(),
				getType(),
				liveIndexer.version()
			));
		}

		return Future.succeededFuture();
	}

	private Future<Void> startLoad(IndexerRecord loadIndexer) {
		if (commandService == null) {
			return Future.succeededFuture();
		}

		return commandService.submit(new StartLoadCommand(loadIndexer.id(), 0L));
	}

	private String liveQueueName(CreateLoadCommand create) {
		return create.getLiveQueueName() == null
			? create.getQueueName() + "--live"
			: create.getLiveQueueName();
	}
}
