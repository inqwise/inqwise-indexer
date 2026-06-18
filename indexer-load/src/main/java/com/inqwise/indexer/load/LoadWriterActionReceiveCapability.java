package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerActionType;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.providers.ActionReceiveReadiness;
import com.inqwise.indexer.providers.IndexerActionReceiveCapability;
import com.inqwise.indexer.providers.PrepareIndexerForActionsRequest;
import com.inqwise.indexer.providers.PreparedIndexers;
import com.inqwise.indexer.provisioning.CreateIndexerOperation;

import io.vertx.core.Future;

public class LoadWriterActionReceiveCapability implements IndexerActionReceiveCapability {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final CreateIndexerOperation createIndexer;

	public LoadWriterActionReceiveCapability(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.createIndexer = new CreateIndexerOperation(metadataRepository);
	}

	@Override
	public Future<ActionReceiveReadiness> canReceive(IndexerRecord indexer, IndexerActionItem action) {
		Objects.requireNonNull(indexer, "indexer");
		Objects.requireNonNull(action, "action");

		if (indexer.role() != IndexerRole.LOAD_WRITER || !isLiveAction(action)) {
			return Future.succeededFuture(ActionReceiveReadiness.NO);
		}

		return loadRepository.getByIndexerId(indexer.id())
			.map(found -> {
				if (found.isEmpty()) {
					return ActionReceiveReadiness.NO;
				}

				IndexerLoadRecord load = found.get();
				if (!isActive(load.state())
					|| load.liveWriterPolicy() != LiveWriterPolicy.CREATE_ON_FIRST_LIVE_ACTION
					|| load.liveIndexerId() != null) {
					return ActionReceiveReadiness.NO;
				}

				return ActionReceiveReadiness.REQUIRES_PREPARE;
			});
	}

	@Override
	public Future<PreparedIndexers> prepareToReceive(PrepareIndexerForActionsRequest request) {
		Objects.requireNonNull(request, "request");
		IndexerRecord loadIndexer = Objects.requireNonNull(request.indexer(), "indexer");

		return loadRepository.getByIndexerId(loadIndexer.id())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + loadIndexer.id())))
			.compose(load -> prepare(load, loadIndexer, true));
	}

	private Future<PreparedIndexers> prepare(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer,
		boolean retryStaleState
	) {
		if (load.liveIndexerId() != null) {
			return metadataRepository.getIndexerById(load.liveIndexerId())
				.compose(found -> found
					.map(indexer -> Future.succeededFuture(new PreparedIndexers(
						java.util.List.of(indexer),
						false
					)))
					.orElseGet(() -> Future.failedFuture(
						"Linked live writer not found: " + load.liveIndexerId()
					)));
		}

		if (!isActive(load.state())) {
			return Future.failedFuture("Indexer load is not active: " + load.state());
		}
		if (load.liveWriterPolicy() != LiveWriterPolicy.CREATE_ON_FIRST_LIVE_ACTION) {
			return Future.failedFuture("Indexer load does not allow lazy live writer: " + load.indexerId());
		}
		if (!load.targetId().equals(loadIndexer.targetId())) {
			return Future.failedFuture("Load target mismatch: " + load.indexerId());
		}

		return createIndexer.create(new InsertIndexer(
			"live" + load.indexerId(),
			load.targetId(),
			loadIndexer.targetName(),
			loadIndexer.indexName(),
			liveQueueName(loadIndexer),
			IndexerType.INDEX,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.ATTACHED,
			IndexerRuntimeState.ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		)).compose(liveWriter -> attachPreparedLiveWriter(load, loadIndexer, liveWriter, retryStaleState));
	}

	private Future<PreparedIndexers> attachPreparedLiveWriter(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer,
		IndexerRecord liveWriter,
		boolean retryStaleState
	) {
		return loadRepository.attachLiveWriterIfAbsent(new AttachLiveWriterRequest(
			load.indexerId(),
			liveWriter.id(),
			load.version()
		)).compose(attached -> preparedLiveWriter(liveWriter, attached))
			.recover(error -> recoverStaleState(load, loadIndexer, retryStaleState, error));
	}

	private Future<PreparedIndexers> recoverStaleState(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer,
		boolean retryStaleState,
		Throwable error
	) {
		if (!isVersionConflict(error)) {
			return Future.failedFuture(error);
		}

		if (!retryStaleState) {
			return Future.failedFuture(new RetryableStaleStateException(
				"Indexer load changed while preparing live writer: " + load.indexerId(),
				error
			));
		}

		return loadRepository.getByIndexerId(load.indexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())))
			.compose(reloaded -> prepare(reloaded, loadIndexer, false));
	}

	private Future<PreparedIndexers> preparedLiveWriter(
		IndexerRecord candidate,
		AttachLiveWriterResult attached
	) {
		if (attached.liveIndexerId().equals(candidate.id())) {
			return Future.succeededFuture(new PreparedIndexers(java.util.List.of(candidate), true));
		}

		return metadataRepository.getIndexerById(attached.liveIndexerId())
			.compose(found -> found
				.map(winner -> Future.succeededFuture(new PreparedIndexers(
					java.util.List.of(winner),
					false
				)))
				.orElseGet(() -> Future.failedFuture(
					"Linked live writer not found: " + attached.liveIndexerId()
				)));
	}

	private boolean isLiveAction(IndexerActionItem action) {
		return action.getActionType() == IndexerActionType.PUT_DOCUMENT
			|| action.getActionType() == IndexerActionType.REMOVE_DOCUMENT;
	}

	private boolean isActive(IndexerLoadState state) {
		return state != IndexerLoadState.PUBLISHED
			&& state != IndexerLoadState.FAILED
			&& state != IndexerLoadState.CANCELLED;
	}

	private boolean isVersionConflict(Throwable error) {
		return error.getMessage() != null && error.getMessage().contains("version conflict");
	}

	private String liveQueueName(IndexerRecord loadIndexer) {
		return loadIndexer.queueName() + "--live";
	}
}
