package com.inqwise.indexer.catalog.indexers;

import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.UpdateIndexerMutationState;
import com.inqwise.indexer.metadata.UpdateIndexerRuntimeState;

import io.vertx.core.Future;

public final class MetadataIndexerOperations implements IndexerOperations {
	public static final String DELETE_CHANGE_TYPE = "indexer.delete";

	private final DocumentStoreMetadataRepository repository;
	private final MetadataChangeNotifier metadataChangeNotifier;

	public MetadataIndexerOperations(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
	}

	@Override
	public Future<Optional<IndexerDeletionResult>> markDeleting(MarkIndexerDeletingRequest request) {
		Objects.requireNonNull(request, "request");

		return repository.getIndexerById(request.indexerId())
			.compose(found -> found
				.map(indexer -> markDeleting(indexer, request.expectedVersion()))
				.orElseGet(() -> Future.succeededFuture(Optional.empty())))
			.map(marked -> marked.map(this::toDeletionResult));
	}

	private IndexerDeletionResult toDeletionResult(IndexerRecord indexer) {
		return new IndexerDeletionResult(
			indexer.id(),
			indexer.targetId(),
			indexer.mutationState(),
			indexer.runtimeState(),
			indexer.version()
		);
	}

	private Future<Optional<IndexerRecord>> markDeleting(
		IndexerRecord indexer,
		long expectedVersion
	) {
		boolean mutationChanged = indexer.mutationState() != MutationState.DELETING;
		Future<Void> marked = mutationChanged
			? repository.updateIndexerMutationState(new UpdateIndexerMutationState(
				indexer.id(),
				MutationState.DELETING,
				expectedVersion
			))
			: Future.succeededFuture();

		return marked
			.compose(ignored -> reload(indexer.id()))
			.compose(markedIndexer -> {
				boolean runtimeChanged =
					markedIndexer.runtimeState() != IndexerRuntimeState.NON_ACTIVE;
				Future<Void> deactivated = runtimeChanged
					? repository.updateIndexerRuntimeState(new UpdateIndexerRuntimeState(
						markedIndexer.id(),
						IndexerRuntimeState.NON_ACTIVE,
						markedIndexer.version()
					))
					: Future.succeededFuture();

				return deactivated
					.compose(ignored -> reload(markedIndexer.id()))
					.compose(updated -> publishIfChanged(
						updated,
						mutationChanged || runtimeChanged
					));
			});
	}

	private Future<IndexerRecord> reload(Integer indexerId) {
		return repository.getIndexerById(indexerId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer not found: " + indexerId)));
	}

	private Future<Optional<IndexerRecord>> publishIfChanged(
		IndexerRecord indexer,
		boolean changed
	) {
		if (changed) {
			return metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
				indexer.id(),
				indexer.targetId(),
				DELETE_CHANGE_TYPE,
				indexer.version()
			)).map(ignored -> Optional.of(indexer));
		}

		return metadataChangeNotifier.confirmTargetInvalidated(indexer.targetId())
			.map(ignored -> Optional.of(indexer));
	}
}
