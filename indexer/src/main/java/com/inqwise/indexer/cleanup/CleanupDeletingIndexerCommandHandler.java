package com.inqwise.indexer.cleanup;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.FinalizeIndexerDeletion;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;

public final class CleanupDeletingIndexerCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository repository;
	private final IndexerQueueResourceManager queueResources;
	private final IndexerDocumentIndexResourceManager documentIndexResources;

	public CleanupDeletingIndexerCommandHandler(
		DocumentStoreMetadataRepository repository,
		IndexerQueueResourceManager queueResources,
		IndexerDocumentIndexResourceManager documentIndexResources
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.queueResources = Objects.requireNonNull(queueResources, "queueResources");
		this.documentIndexResources = Objects.requireNonNull(
			documentIndexResources,
			"documentIndexResources"
		);
	}

	@Override
	public String getType() {
		return CleanupDeletingIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CleanupDeletingIndexerCommand cleanup =
			new CleanupDeletingIndexerCommand(command.toJson());

		return repository.getIndexerById(cleanup.getIndexerId())
			.compose(found -> found
				.map(this::cleanup)
				.orElseGet(Future::succeededFuture));
	}

	private Future<Void> cleanup(IndexerRecord indexer) {
		if (indexer.mutationState() != MutationState.DELETING) {
			return Future.failedFuture("Indexer is not deleting: " + indexer.id());
		}

		return queueResources.delete(indexer.queueName())
			.compose(ignored -> deleteDocumentIndex(indexer))
			.compose(ignored -> repository.finalizeIndexerDeletion(
				new FinalizeIndexerDeletion(indexer.id(), indexer.version())
			));
	}

	private Future<Void> deleteDocumentIndex(IndexerRecord indexer) {
		return indexer.indexOwnership() == IndexResourceOwnership.OWNER
			? documentIndexResources.delete(indexer.indexName())
			: Future.succeededFuture();
	}
}
