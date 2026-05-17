package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerRuntimeStatus;
import com.inqwise.indexer.metadata.UpdateIndexerRuntimeStatus;

import io.vertx.core.Future;

public class ActivateIndexerCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLifecycleEventBus eventBus;

	public ActivateIndexerCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLifecycleEventBus eventBus
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
	}

	@Override
	public String getType() {
		return ActivateIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		ActivateIndexerCommand activate = new ActivateIndexerCommand(command.toJson());
		return metadataRepository.getIndexerById(activate.getIndexerId())
			.compose(found -> {
				if (found.isEmpty()) {
					return Future.failedFuture("Indexer not found: " + activate.getIndexerId());
				}

				IndexerRecord indexer = found.get();
				if (indexer.runtimeStatus() == IndexerRuntimeStatus.DELETED) {
					return Future.failedFuture("Cannot activate deleted indexer: " + activate.getIndexerId());
				}

				if (indexer.runtimeStatus() == IndexerRuntimeStatus.STARTED
					|| indexer.runtimeStatus() == IndexerRuntimeStatus.COMPLETED) {
					return publish(indexer);
				}

				return metadataRepository.updateIndexerRuntimeStatus(new UpdateIndexerRuntimeStatus(
					activate.getIndexerId(),
					IndexerRuntimeStatus.STARTED,
					indexer.version()
				))
					.compose(ignored -> metadataRepository.getIndexerById(activate.getIndexerId()))
					.compose(updated -> updated
						.map(this::publish)
						.orElseGet(() -> Future.failedFuture(
							"Indexer not found: " + activate.getIndexerId()
						)));
			});
	}

	private Future<Void> publish(IndexerRecord indexer) {
		return eventBus.publish(new IndexerLifecycleChanged(
			indexer.id(),
			getType(),
			indexer.version()
		));
	}
}
