package com.inqwise.indexer.commands;

import java.util.Objects;
import java.util.regex.Pattern;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.UpdateIndexerQueueName;

import io.vertx.core.Future;

public class ResetIndexerQueueCommandHandler implements CommandHandler {
	private static final Pattern RESET_VERSION_SUFFIX = Pattern.compile("-v\\d+$");

	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLifecycleEventBus eventBus;
	private final IndexerQueueResourceManager queueResourceManager;

	public ResetIndexerQueueCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLifecycleEventBus eventBus,
		IndexerQueueResourceManager queueResourceManager
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		this.queueResourceManager = Objects.requireNonNull(
			queueResourceManager,
			"queueResourceManager"
		);
	}

	@Override
	public String getType() {
		return ResetIndexerQueueCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		ResetIndexerQueueCommand reset = new ResetIndexerQueueCommand(command.toJson());

		return metadataRepository.getIndexerById(reset.getIndexerId())
			.compose(found -> {
				if (found.isEmpty()) {
					return Future.failedFuture("Indexer not found: " + reset.getIndexerId());
				}

				IndexerRecord indexer = found.get();
				if (indexer.status() != IndexerStatus.AVAILABLE
					|| indexer.mutationState() == MutationState.DELETING) {
					return Future.failedFuture("Cannot reset deleted indexer queue: " + reset.getIndexerId());
				}

				if (indexer.version() != reset.getExpectedVersion()) {
					return Future.failedFuture(
						"Indexer version conflict for id " + indexer.id()
							+ ": expected " + reset.getExpectedVersion()
							+ " but was " + indexer.version()
					);
				}

				String newQueueName = nextQueueName(indexer.queueName(), indexer.version() + 1);
				return queueResourceManager.ensure(newQueueName)
					.compose(ignored -> metadataRepository.updateIndexerQueueName(
						new UpdateIndexerQueueName(
							indexer.id(),
							newQueueName,
							reset.getExpectedVersion()
						)
					))
					.compose(ignored -> metadataRepository.getIndexerById(indexer.id()))
					.compose(updated -> updated
						.map(this::publish)
						.orElseGet(() -> Future.failedFuture("Indexer not found: " + indexer.id())));
			});
	}

	private Future<Void> publish(IndexerRecord indexer) {
		return eventBus.publish(new IndexerMetadataChanged(
			indexer.id(),
			getType(),
			indexer.version()
		));
	}

	private String nextQueueName(String queueName, long resultingVersion) {
		Objects.requireNonNull(queueName, "queueName");
		String baseQueueName = RESET_VERSION_SUFFIX.matcher(queueName).replaceFirst("");
		return baseQueueName + "-v" + resultingVersion;
	}
}
