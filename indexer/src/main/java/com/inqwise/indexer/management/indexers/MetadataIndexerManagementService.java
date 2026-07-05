package com.inqwise.indexer.management.indexers;

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

public final class MetadataIndexerManagementService implements IndexerManagementService {
	private final DocumentStoreMetadataRepository repository;
	private final MetadataChangeNotifier notifier;

	public MetadataIndexerManagementService(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier notifier
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.notifier = Objects.requireNonNull(notifier, "notifier");
	}

	@Override
	public Future<IndexerRecord> activate(IndexerRuntimeStateRequest request) {
		return change(request, IndexerRuntimeState.ACTIVE, "indexer.activate");
	}

	@Override
	public Future<IndexerRecord> deactivate(IndexerRuntimeStateRequest request) {
		return change(request, IndexerRuntimeState.NON_ACTIVE, "indexer.deactivate");
	}

	private Future<IndexerRecord> change(
		IndexerRuntimeStateRequest request,
		IndexerRuntimeState desiredState,
		String changeType
	) {
		Objects.requireNonNull(request, "request");
		return load(request.indexerId()).compose(indexer -> {
			if (alreadyApplied(request, indexer, desiredState)) {
				return publish(indexer, changeType, indexer.version()).map(indexer);
			}
			if (indexer.version() != request.expectedVersion()) {
				return Future.failedFuture("Indexer version conflict for id " + indexer.id()
					+ ": expected " + request.expectedVersion() + " but was " + indexer.version());
			}
			if (indexer.status() != IndexerStatus.AVAILABLE
				|| indexer.mutationState() == MutationState.DELETING) {
				return Future.failedFuture("Cannot " + action(desiredState)
					+ " deleted indexer: " + indexer.id());
			}
			if (indexer.runtimeState() == desiredState) {
				return Future.failedFuture("Indexer is already "
					+ (desiredState == IndexerRuntimeState.ACTIVE ? "active: " : "inactive: ")
					+ indexer.id());
			}
			long resultingVersion = request.expectedVersion() + 1L;
			return repository.updateIndexerRuntimeState(new UpdateIndexerRuntimeState(
				indexer.id(),
				desiredState,
				request.expectedVersion()
			)).compose(ignored -> publish(indexer, changeType, resultingVersion))
				.compose(ignored -> load(indexer.id()));
		});
	}

	private String action(IndexerRuntimeState desiredState) {
		return desiredState == IndexerRuntimeState.ACTIVE ? "activate" : "deactivate";
	}

	private boolean alreadyApplied(
		IndexerRuntimeStateRequest request,
		IndexerRecord indexer,
		IndexerRuntimeState desiredState
	) {
		return request.expectedVersion() >= 0L
			&& request.expectedVersion() < Long.MAX_VALUE
			&& indexer.version() == request.expectedVersion() + 1L
			&& indexer.runtimeState() == desiredState
			&& indexer.mutationState() != MutationState.DELETING;
	}

	private Future<Void> publish(IndexerRecord indexer, String changeType, long version) {
		return notifier.indexerChanged(new IndexerMetadataChanged(
			indexer.id(), indexer.targetId(), changeType, version
		));
	}

	private Future<IndexerRecord> load(Integer id) {
		return repository.getIndexerById(id).compose(found -> found
			.map(Future::succeededFuture)
			.orElseGet(() -> Future.failedFuture("Indexer not found: " + id)));
	}
}
