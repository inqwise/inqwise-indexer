package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.ReadinessState;
import com.inqwise.indexer.metadata.UpdatePublicationReadiness;

import io.vertx.core.Future;

public class MarkIndexReadyCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository repository;

	public MarkIndexReadyCommandHandler(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public String getType() {
		return MarkIndexReadyCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		MarkIndexReadyCommand ready = new MarkIndexReadyCommand(command.toJson());

		return repository.updatePublicationReadiness(new UpdatePublicationReadiness(
			ready.getPublicationId(),
			ReadinessState.READY,
			ready.getReason(),
			ready.getExpectedVersion()
		));
	}
}
