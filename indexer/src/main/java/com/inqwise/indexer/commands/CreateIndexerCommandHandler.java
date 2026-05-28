package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;

import io.vertx.core.Future;

public class CreateIndexerCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository repository;
	private final IndexerLifecycleEventBus eventBus;

	public CreateIndexerCommandHandler(
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
	}

	@Override
	public String getType() {
		return CreateIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CreateIndexerCommand create = new CreateIndexerCommand(command.toJson());

		return repository.insertIndexer(new InsertIndexer(
			create.getPrefix(),
			create.getTargetId(),
			create.getTargetName(),
			create.getIndexName(),
			create.getQueueName(),
			create.getIndexerType(),
			create.getRole(),
			create.getIndexOwnership(),
			create.getRuntimeState(),
			create.getPublicationState(),
			create.getMutationState()
		)).compose(indexerId -> eventBus.publish(new IndexerMetadataChanged(
			indexerId,
			getType(),
			0L
		)));
	}
}
