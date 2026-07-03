package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.MetadataChangeNotifier;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.UpdateIndexerRuntimeState;

import io.vertx.core.Future;

public class ActivateIndexerCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final MetadataChangeNotifier metadataChangeNotifier;

	public ActivateIndexerCommandHandler(
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
				if (alreadyApplied(activate, indexer)) {
					return publish(indexer);
				}

				if (indexer.version() != activate.getExpectedVersion()) {
					return Future.failedFuture(versionConflict(
						indexer,
						activate.getExpectedVersion()
					));
				}

				if (indexer.status() != IndexerStatus.AVAILABLE
					|| indexer.mutationState() == MutationState.DELETING) {
					return Future.failedFuture("Cannot activate deleted indexer: " + activate.getIndexerId());
				}

				if (indexer.runtimeState() == IndexerRuntimeState.ACTIVE) {
					return Future.failedFuture("Indexer is already active: " + indexer.id());
				}

				return metadataRepository.updateIndexerRuntimeState(new UpdateIndexerRuntimeState(
					activate.getIndexerId(),
					IndexerRuntimeState.ACTIVE,
					activate.getExpectedVersion()
				))
					.compose(ignored -> publish(
						indexer,
						activate.getExpectedVersion() + 1L
					));
			});
	}

	private boolean alreadyApplied(ActivateIndexerCommand activate, IndexerRecord indexer) {
		return activate.getExpectedVersion() >= 0L
			&& activate.getExpectedVersion() < Long.MAX_VALUE
			&& indexer.version() == activate.getExpectedVersion() + 1L
			&& indexer.runtimeState() == IndexerRuntimeState.ACTIVE
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
