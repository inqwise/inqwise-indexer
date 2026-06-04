package com.inqwise.indexer.commands;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.provisioning.CreateIndexerOperation;

import io.vertx.core.Future;

public class CreateIndexerCommandHandler implements CommandHandler {
	private final CreateIndexerOperation createIndexer;
	private final IndexerLifecycleEventBus eventBus;

	public CreateIndexerCommandHandler(
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus
	) {
		this.createIndexer = new CreateIndexerOperation(repository);
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
	}

	@Override
	public String getType() {
		return CreateIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CreateIndexerCommand create = new CreateIndexerCommand(command.toJson());

		return createIndexer.create(new InsertIndexer(
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
		)).compose(indexer -> eventBus.publish(new IndexerMetadataChanged(
			indexer.id(),
			getType(),
			indexer.version()
		)));
	}
}
