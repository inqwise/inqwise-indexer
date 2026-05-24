package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.UpdateIndexerMutationState;
import com.inqwise.indexer.metadata.UpdateIndexerRuntimeState;

import io.vertx.core.Future;

public class DeleteIndexerCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLifecycleEventBus eventBus;

	public DeleteIndexerCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLifecycleEventBus eventBus
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
	}

	@Override
	public String getType() {
		return DeleteIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		DeleteIndexerCommand delete = new DeleteIndexerCommand(command.toJson());
		return metadataRepository.getIndexerById(delete.getIndexerId())
			.compose(found -> {
				if (found.isEmpty()) {
					return Future.succeededFuture();
				}

				IndexerRecord indexer = found.get();
				if (delete.getExpectedVersion() == null) {
					return Future.failedFuture(
						"Expected version is required for metadata indexer delete: "
							+ delete.getIndexerId()
					);
				}

				return markDeleting(indexer, delete.getExpectedVersion())
					.compose(this::deactivate)
					.compose(this::publish);
			});
	}

	private Future<IndexerRecord> markDeleting(IndexerRecord indexer, long expectedVersion) {
		Future<Void> updated = indexer.mutationState() == MutationState.DELETING
			? Future.succeededFuture()
			: metadataRepository.updateIndexerMutationState(new UpdateIndexerMutationState(
				indexer.id(),
				MutationState.DELETING,
				expectedVersion
			));

		return updated
			.compose(ignored -> metadataRepository.getIndexerById(indexer.id()))
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer not found: " + indexer.id())));
	}

	private Future<IndexerRecord> deactivate(IndexerRecord indexer) {
		Future<Void> updated = indexer.runtimeState() == IndexerRuntimeState.NON_ACTIVE
			? Future.succeededFuture()
			: metadataRepository.updateIndexerRuntimeState(new UpdateIndexerRuntimeState(
				indexer.id(),
				IndexerRuntimeState.NON_ACTIVE,
				indexer.version()
			));

		return updated
			.compose(ignored -> metadataRepository.getIndexerById(indexer.id()))
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer not found: " + indexer.id())));
	}

	private Future<Void> publish(IndexerRecord indexer) {
		return eventBus.publish(new IndexerMetadataChanged(
			indexer.id(),
			getType(),
			indexer.version()
		));
	}
}
