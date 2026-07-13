package com.inqwise.indexer.load.commands;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadState;


import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.ReplacePublishedIndexer;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;

import io.vertx.core.Future;

public class PublishLoadCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final IndexerLifecycleEventBus eventBus;
	private final CommandService commandService;

	public PublishLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus
	) {
		this(metadataRepository, loadRepository, eventBus, null);
	}

	public PublishLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus,
		CommandService commandService
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		this.commandService = commandService;
	}

	@Override
	public String getType() {
		return PublishLoadCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		PublishLoadCommand publish = new PublishLoadCommand(command.toJson());

		return loadRepository.getByIndexerId(publish.getIndexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Indexer load not found: " + publish.getIndexerId()
				)))
			.compose(load -> validateLoadVersion(load, publish.getExpectedLoadVersion())
				.compose(valid -> publish(load)));
	}

	private Future<IndexerLoadRecord> validateLoadVersion(IndexerLoadRecord load, long expectedVersion) {
		if (load.version() != expectedVersion) {
			return Future.failedFuture(
				"Indexer load version conflict for id " + load.indexerId() + ": expected "
					+ expectedVersion + " but was " + load.version()
			);
		}
		return Future.succeededFuture(load);
	}

	private Future<Void> publish(IndexerLoadRecord load) {
		return metadataRepository.getIndexerById(load.indexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Load indexer not found: " + load.indexerId())))
			.compose(loadIndexer -> resolveCandidate(load)
					.compose(candidate -> validate(load, loadIndexer, candidate)
						.compose(valid -> metadataRepository.listPublishedIndexersByTargetId(load.targetId())
							.compose(previous -> replace(load, loadIndexer, candidate, previous)
								.compose(ignored -> markPublished(load))
								.compose(ignored -> publishEvents(load, loadIndexer, candidate, previous))
								.compose(ignored -> cleanup(load, previous))))));
	}

	private Future<IndexerRecord> resolveCandidate(IndexerLoadRecord load) {
		Integer candidateId = load.liveIndexerId() == null
			? load.indexerId()
			: load.liveIndexerId();
		return metadataRepository.getIndexerById(candidateId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Candidate indexer not found: " + candidateId)));
	}

	private Future<Void> validate(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer,
		IndexerRecord candidate
	) {
		if (!load.targetId().equals(loadIndexer.targetId()) || !load.targetId().equals(candidate.targetId())) {
			return Future.failedFuture("Load target mismatch: " + load.indexerId());
		}

		if (load.liveIndexerId() != null) {
			if (!candidate.indexName().equals(loadIndexer.indexName())) {
				return Future.failedFuture("Linked live writer index mismatch: " + candidate.id());
			}
			if (load.lastBarrierId() == null || load.lastBarrierReachedAt() == null) {
				return Future.failedFuture("Catch-up barrier was not reached for load: " + load.indexerId());
			}
			if (load.state() != IndexerLoadState.CATCH_UP_READY && load.state() != IndexerLoadState.APPROVED) {
				return Future.failedFuture("Load is not publishable: " + load.state());
			}
		} else if (load.state() != IndexerLoadState.HISTORICAL_COMPLETE
			&& load.state() != IndexerLoadState.APPROVED) {
			return Future.failedFuture("Load is not publishable: " + load.state());
		}

		if (load.reviewRequired() && load.approvedAt() == null) {
			return Future.failedFuture("Load publication review is not approved: " + load.indexerId());
		}

		if (candidate.status() != IndexerStatus.AVAILABLE
			|| candidate.provisioningState() != IndexerProvisioningState.READY
			|| candidate.runtimeState() != IndexerRuntimeState.ACTIVE
			|| candidate.mutationState() != MutationState.WRITABLE
			|| candidate.publicationState() != PublicationState.UNPUBLISHED) {
			return Future.failedFuture("Candidate indexer is not publishable: " + candidate.id());
		}

		return Future.succeededFuture();
	}

	private Future<Void> replace(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer,
		IndexerRecord candidate,
		List<IndexerRecord> previous
	) {
		if (previous.size() > 1) {
			return Future.failedFuture("Multiple published indexers for target: " + load.targetId());
		}

		IndexerRecord oldPublished = previous.isEmpty() ? null : previous.get(0);
		return metadataRepository.replacePublishedIndexer(new ReplacePublishedIndexer(
			load.targetId(),
			candidate.id(),
			candidate.version(),
			oldPublished == null ? null : oldPublished.id(),
			oldPublished == null ? null : oldPublished.version(),
			load.liveIndexerId() == null ? null : loadIndexer.id(),
			load.liveIndexerId() == null ? null : loadIndexer.version()
		));
	}

	private Future<Void> markPublished(IndexerLoadRecord load) {
		return loadRepository.updateState(new UpdateIndexerLoadState(
			load.indexerId(),
			IndexerLoadState.PUBLISHED,
			load.version()
		));
	}

	private Future<Void> publishEvents(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer,
		IndexerRecord candidate,
		List<IndexerRecord> previous
	) {
		eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
			candidate.id(),
			candidate.targetId(),
			getType(),
			candidate.version() + 1
		));

		if (load.liveIndexerId() != null && !loadIndexer.id().equals(candidate.id())) {
			eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
				loadIndexer.id(),
				loadIndexer.targetId(),
				getType(),
				loadIndexer.version() + 1
			));
		}

		if (!previous.isEmpty()) {
			IndexerRecord oldPublished = previous.get(0);
			eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
				oldPublished.id(),
				oldPublished.targetId(),
				getType(),
				oldPublished.version() + 1
			));
		}

		return Future.succeededFuture();
	}

	private Future<Void> cleanup(IndexerLoadRecord load, List<IndexerRecord> previous) {
		if (commandService == null) {
			return Future.succeededFuture();
		}

		IndexerRecord oldPublished = previous.isEmpty() ? null : previous.get(0);
		return commandService.submit(new CleanupLoadCommand(
			load.indexerId(),
			oldPublished == null ? null : oldPublished.id()
		));
	}
}
