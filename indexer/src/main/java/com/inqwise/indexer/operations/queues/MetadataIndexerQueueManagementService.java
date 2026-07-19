package com.inqwise.indexer.operations.queues;

import java.util.Objects;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.cleanup.CleanupResetIndexerQueueCommand;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.metadata.UpdateIndexerQueueName;

import io.vertx.core.Future;

/**
 * Metadata-backed queue reset orchestration. It provisions the replacement
 * queue identity and delegates retired-queue deletion to durable cleanup.
 */
public final class MetadataIndexerQueueManagementService
	implements IndexerQueueManagementService {
	private static final String CHANGE_TYPE = "indexer.queue.reset";
	private final DocumentStoreMetadataRepository repository;
	private final MetadataChangeNotifier notifier;
	private final IndexerQueueResourceManager queueResources;
	private final CommandService commandService;

	public MetadataIndexerQueueManagementService(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier notifier,
		IndexerQueueResourceManager queueResources,
		CommandService commandService
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.notifier = Objects.requireNonNull(notifier, "notifier");
		this.queueResources = Objects.requireNonNull(queueResources, "queueResources");
		this.commandService = Objects.requireNonNull(commandService, "commandService");
	}

	@Override
	public Future<Void> reset(ResetIndexerQueueRequest request) {
		Objects.requireNonNull(request, "request");
		return load(request.indexerId()).compose(indexer -> reset(indexer, request));
	}

	private Future<Void> reset(IndexerRecord indexer, ResetIndexerQueueRequest request) {
		if (request.expectedVersion() < 0L || request.expectedVersion() == Long.MAX_VALUE) {
			return Future.failedFuture("Invalid expected version for indexer queue reset: "
				+ request.expectedVersion());
		}
		if (indexer.status() != IndexerStatus.AVAILABLE
			|| indexer.mutationState() == MutationState.DELETING) {
			return Future.failedFuture("Cannot reset deleted indexer queue: " + indexer.id());
		}
		String newQueueName = nextQueueName(request.expectedQueueName(), request.expectedVersion());
		if (indexer.version() == request.expectedVersion() + 1L
			&& indexer.queueName().equals(newQueueName)) {
			return complete(indexer, request.expectedQueueName());
		}
		if (indexer.version() != request.expectedVersion()
			|| !indexer.queueName().equals(request.expectedQueueName())) {
			return Future.failedFuture("Indexer queue state conflict for id " + indexer.id()
				+ ": expected queue " + request.expectedQueueName() + " at version "
				+ request.expectedVersion() + " but was queue " + indexer.queueName()
				+ " at version " + indexer.version());
		}
		long resultingVersion = request.expectedVersion() + 1L;
		return queueResources.ensure(newQueueName)
			.compose(ignored -> repository.updateIndexerQueueName(UpdateIndexerQueueName.builder()
				.withId(indexer.id())
				.withQueueName(newQueueName)
				.withExpectedVersion(request.expectedVersion())
				.build()))
			.compose(ignored -> complete(indexer, resultingVersion, request.expectedQueueName()));
	}

	private Future<Void> complete(IndexerRecord indexer, String oldQueueName) {
		return complete(indexer, indexer.version(), oldQueueName);
	}

	private Future<Void> complete(
		IndexerRecord indexer,
		long version,
		String oldQueueName
	) {
		return notifier.indexerChanged(IndexerMetadataChanged.builder()
			.withIndexerId(indexer.id())
			.withTargetId(indexer.targetId())
			.withCommandType(CHANGE_TYPE)
			.withVersion(version)
			.build()).compose(ignored -> commandService.submit(new CleanupResetIndexerQueueCommand(
			indexer.id(), oldQueueName
		)));
	}

	private String nextQueueName(String queueName, long expectedVersion) {
		String suffix = "-v" + expectedVersion;
		String base = queueName.endsWith(suffix)
			? queueName.substring(0, queueName.length() - suffix.length())
			: queueName;
		return base + "-v" + (expectedVersion + 1L);
	}

	private Future<IndexerRecord> load(Integer id) {
		return repository.getIndexerById(id).compose(found -> found
			.map(Future::succeededFuture)
			.orElseGet(() -> Future.failedFuture("Indexer not found: " + id)));
	}
}
