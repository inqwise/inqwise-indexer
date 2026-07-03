package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.UpdateIndexerPublicationState;

import io.vertx.core.Future;

public class RetireIndexCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository repository;

	public RetireIndexCommandHandler(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public String getType() {
		return RetireIndexCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		RetireIndexCommand retire = new RetireIndexCommand(command.toJson());

		return repository.getIndexerById(retire.getIndexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Indexer not found: " + retire.getIndexerId()
				)))
			.compose(indexer -> {
				if (alreadyApplied(retire, indexer.version(), indexer.publicationState())) {
					return Future.succeededFuture();
				}

				if (indexer.version() != retire.getExpectedVersion()) {
					return Future.failedFuture(
						"Indexer version conflict for id " + indexer.id() + ": expected "
							+ retire.getExpectedVersion() + " but was " + indexer.version()
					);
				}

				if (indexer.publicationState() == PublicationState.RETIRED) {
					return Future.failedFuture("Index is already retired: " + indexer.indexName());
				}

				return repository.updateIndexerPublicationState(new UpdateIndexerPublicationState(
					retire.getIndexerId(),
					PublicationState.RETIRED,
					retire.getExpectedVersion()
				));
			});
	}

	private boolean alreadyApplied(
		RetireIndexCommand retire,
		long currentVersion,
		PublicationState publicationState
	) {
		return retire.getExpectedVersion() >= 0L
			&& retire.getExpectedVersion() < Long.MAX_VALUE
			&& currentVersion == retire.getExpectedVersion() + 1L
			&& publicationState == PublicationState.RETIRED;
	}
}
