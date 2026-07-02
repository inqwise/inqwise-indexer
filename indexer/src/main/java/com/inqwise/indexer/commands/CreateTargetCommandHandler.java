package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.MetadataChangeNotifier;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.provisioning.CreateTargetOperation;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;

public class CreateTargetCommandHandler implements CommandHandler {
	private final CreateTargetOperation operation;

	public CreateTargetCommandHandler(
		DocumentStoreMetadataRepository repository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources,
		MetadataChangeNotifier metadataChangeNotifier
	) {
		this.operation = new CreateTargetOperation(
			Objects.requireNonNull(repository, "repository"),
			Objects.requireNonNull(targetDefinitionProvider, "targetDefinitionProvider"),
			indexerDefinitionProvider,
			documentIndexResources,
			queueResources,
			metadataChangeNotifier
		);
	}

	@Override
	public String getType() {
		return CreateTargetCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CreateTargetCommand create = new CreateTargetCommand(command.toJson());
		return operation.create(create).mapEmpty();
	}
}
