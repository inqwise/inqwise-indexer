package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.MetadataChangeNotifier;
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
	private final MetadataChangeNotifier metadataChangeNotifier;

	public CreateIndexerCommandHandler(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier
	) {
		this(
			repository,
			defaultDefinitionProvider(),
			IndexerDocumentIndexResourceManager.NOOP,
			IndexerQueueResourceManager.NOOP,
			metadataChangeNotifier
		);
	}

	public CreateIndexerCommandHandler(
		DocumentStoreMetadataRepository repository,
		IndexerDefinitionProvider definitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources,
		MetadataChangeNotifier metadataChangeNotifier
	) {
		this.provisioningService = new IndexerProvisioningService(
			repository,
			definitionProvider,
			documentIndexResources,
			queueResources
		);
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
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
		)).compose(indexer -> metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
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
