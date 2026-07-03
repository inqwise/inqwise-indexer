package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.PublicationRecord;
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

		return repository.getPublicationById(ready.getPublicationId())
			.compose(found -> found
				.map(publication -> markReady(ready, publication))
				.orElseGet(() -> Future.failedFuture(
					"Publication not found: " + ready.getPublicationId()
				)));
	}

	private Future<Void> markReady(
		MarkIndexReadyCommand ready,
		PublicationRecord publication
	) {
		if (alreadyApplied(ready, publication)) {
			return Future.succeededFuture();
		}

		if (publication.version() != ready.getExpectedVersion()) {
			return Future.failedFuture(
				"Publication version conflict for id " + publication.id() + ": expected "
					+ ready.getExpectedVersion() + " but was " + publication.version()
			);
		}

		return repository.updatePublicationReadiness(new UpdatePublicationReadiness(
			ready.getPublicationId(),
			ReadinessState.READY,
			ready.getReason(),
			ready.getExpectedVersion()
		));
	}

	private boolean alreadyApplied(
		MarkIndexReadyCommand ready,
		PublicationRecord publication
	) {
		return ready.getExpectedVersion() >= 0L
			&& ready.getExpectedVersion() < Long.MAX_VALUE
			&& publication.version() == ready.getExpectedVersion() + 1L
			&& publication.readinessState() == ReadinessState.READY
			&& Objects.equals(publication.reason(), ready.getReason());
	}
}
