package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.UpdateIndexerRuntimeState;

import io.vertx.core.Future;

public class DeactivateIndexerCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLifecycleEventBus eventBus;

	public DeactivateIndexerCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLifecycleEventBus eventBus
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
	}

	@Override
	public String getType() {
		return DeactivateIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		DeactivateIndexerCommand deactivate = new DeactivateIndexerCommand(command.toJson());
		return metadataRepository.getIndexerById(deactivate.getIndexerId())
			.compose(found -> {
				if (found.isEmpty()) {
					return Future.failedFuture("Indexer not found: " + deactivate.getIndexerId());
				}

				IndexerRecord indexer = found.get();
				if (indexer.runtimeState() == IndexerRuntimeState.NON_ACTIVE) {
					return publish(indexer);
				}

				return metadataRepository.updateIndexerRuntimeState(new UpdateIndexerRuntimeState(
					deactivate.getIndexerId(),
					IndexerRuntimeState.NON_ACTIVE,
					indexer.version()
				))
					.compose(ignored -> metadataRepository.getIndexerById(deactivate.getIndexerId()))
					.compose(updated -> updated
						.map(this::publish)
						.orElseGet(() -> Future.failedFuture(
							"Indexer not found: " + deactivate.getIndexerId()
						)));
			});
	}

	private Future<Void> publish(IndexerRecord indexer) {
		eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
			indexer.id(),
			indexer.targetId(),
			getType(),
			indexer.version()
		));
		return Future.succeededFuture();
	}
}
