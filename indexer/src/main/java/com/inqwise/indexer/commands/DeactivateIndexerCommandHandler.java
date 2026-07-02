package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.MetadataChangeNotifier;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.UpdateIndexerRuntimeState;

import io.vertx.core.Future;

public class DeactivateIndexerCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final MetadataChangeNotifier metadataChangeNotifier;

	public DeactivateIndexerCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		MetadataChangeNotifier metadataChangeNotifier
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
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
		return metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
			indexer.id(),
			indexer.targetId(),
			getType(),
			indexer.version()
		));
	}
}
