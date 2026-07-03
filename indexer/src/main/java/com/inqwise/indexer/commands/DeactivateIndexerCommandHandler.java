package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.MetadataChangeNotifier;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.MutationState;
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
				if (alreadyApplied(deactivate, indexer)) {
					return publish(indexer);
				}

				if (indexer.version() != deactivate.getExpectedVersion()) {
					return Future.failedFuture(versionConflict(
						indexer,
						deactivate.getExpectedVersion()
					));
				}

				if (indexer.runtimeState() == IndexerRuntimeState.NON_ACTIVE) {
					return Future.failedFuture("Indexer is already inactive: " + indexer.id());
				}

				return metadataRepository.updateIndexerRuntimeState(new UpdateIndexerRuntimeState(
					deactivate.getIndexerId(),
					IndexerRuntimeState.NON_ACTIVE,
					deactivate.getExpectedVersion()
				))
					.compose(ignored -> publish(
						indexer,
						deactivate.getExpectedVersion() + 1L
					));
			});
	}

	private boolean alreadyApplied(DeactivateIndexerCommand deactivate, IndexerRecord indexer) {
		return deactivate.getExpectedVersion() >= 0L
			&& deactivate.getExpectedVersion() < Long.MAX_VALUE
			&& indexer.version() == deactivate.getExpectedVersion() + 1L
			&& indexer.runtimeState() == IndexerRuntimeState.NON_ACTIVE
			&& indexer.mutationState() != MutationState.DELETING;
	}

	private String versionConflict(IndexerRecord indexer, long expectedVersion) {
		return "Indexer version conflict for id " + indexer.id() + ": expected "
			+ expectedVersion + " but was " + indexer.version();
	}

	private Future<Void> publish(IndexerRecord indexer) {
		return publish(indexer, indexer.version());
	}

	private Future<Void> publish(IndexerRecord indexer, long version) {
		return metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
			indexer.id(),
			indexer.targetId(),
			getType(),
			version
		));
	}
}
