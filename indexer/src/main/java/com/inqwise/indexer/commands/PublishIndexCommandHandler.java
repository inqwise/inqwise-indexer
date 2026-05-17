package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerRuntimeStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationRecord;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.ReadinessState;
import com.inqwise.indexer.metadata.UpdateIndexerPublicationState;

import io.vertx.core.Future;

public class PublishIndexCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository repository;

	public PublishIndexCommandHandler(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public String getType() {
		return PublishIndexCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		PublishIndexCommand publish = new PublishIndexCommand(command.toJson());

		return repository.getIndexerById(publish.getIndexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Indexer not found: " + publish.getIndexerId()
				)))
			.compose(indexer -> repository.getPublicationByIndexerId(indexer.id())
				.compose(found -> found
					.map(publication -> validatePublish(publish, indexer, publication))
					.orElseGet(() -> Future.failedFuture(
						"Publication not found for indexer: " + indexer.id()
					))))
			.compose(indexer -> repository.updateIndexerPublicationState(
				new UpdateIndexerPublicationState(
					indexer.id(),
					PublicationState.PUBLISHED,
					publish.getExpectedVersion()
				)
			));
	}

	private Future<IndexerRecord> validatePublish(
		PublishIndexCommand publish,
		IndexerRecord indexer,
		PublicationRecord publication
	) {
		if (indexer.version() != publish.getExpectedVersion()) {
			return Future.failedFuture(
				"Indexer version conflict for id " + indexer.id() + ": expected "
					+ publish.getExpectedVersion() + " but was " + indexer.version()
			);
		}

		if (publication.readinessState() != ReadinessState.READY) {
			return Future.failedFuture("Index is not ready: " + indexer.indexName());
		}

		if (indexer.publicationState() != PublicationState.UNPUBLISHED) {
			return Future.failedFuture("Index is not unpublished: " + indexer.indexName());
		}

		if (indexer.mutationState() == MutationState.DELETING) {
			return Future.failedFuture("Index is deleting: " + indexer.indexName());
		}

		if (indexer.runtimeStatus() != IndexerRuntimeStatus.STARTED
			&& indexer.runtimeStatus() != IndexerRuntimeStatus.COMPLETED) {
			return Future.failedFuture("Indexer is not active: " + indexer.indexName());
		}

		return Future.succeededFuture(indexer);
	}
}
