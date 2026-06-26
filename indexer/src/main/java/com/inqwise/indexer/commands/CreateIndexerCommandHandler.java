package com.inqwise.indexer.commands;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.definitions.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.provisioning.CreateIndexerProvisioningRequest;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;
import com.inqwise.indexer.provisioning.IndexerProvisioningService;

import io.vertx.core.Future;

public class CreateIndexerCommandHandler implements CommandHandler {
	private final IndexerProvisioningService provisioningService;
	private final IndexerLifecycleEventBus eventBus;

	public CreateIndexerCommandHandler(
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus
	) {
		this(
			repository,
			defaultDefinitionProvider(),
			IndexerDocumentIndexResourceManager.NOOP,
			IndexerQueueResourceManager.NOOP,
			eventBus
		);
	}

	public CreateIndexerCommandHandler(
		DocumentStoreMetadataRepository repository,
		IndexerDefinitionProvider definitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources,
		IndexerLifecycleEventBus eventBus
	) {
		this.provisioningService = new IndexerProvisioningService(
			repository,
			definitionProvider,
			documentIndexResources,
			queueResources
		);
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
	}

	@Override
	public String getType() {
		return CreateIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CreateIndexerCommand create = new CreateIndexerCommand(command.toJson());

		return provisioningService.createIndexer(new CreateIndexerProvisioningRequest(
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
			indexer.targetId(),
			getType(),
			indexer.version()
		)));
	}

	private static IndexerDefinitionProvider defaultDefinitionProvider() {
		return new StaticIndexerDefinitionProvider(new IndexerDefinition(
			new IndexDefinition("default", "1", null, null),
			new QueueDefinition(null)
		));
	}
}
