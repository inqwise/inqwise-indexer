package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.MetadataChangeNotifier;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.UpdateIndexerQueueName;

import io.vertx.core.Future;

public class ResetIndexerQueueCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final MetadataChangeNotifier metadataChangeNotifier;
	private final IndexerQueueResourceManager queueResourceManager;
	private final CommandService commandService;

	public ResetIndexerQueueCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueResourceManager queueResourceManager,
		CommandService commandService
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
		this.queueResourceManager = Objects.requireNonNull(
			queueResourceManager,
			"queueResourceManager"
		);
		this.commandService = Objects.requireNonNull(commandService, "commandService");
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
				if (reset.getExpectedVersion() < 0L
					|| reset.getExpectedVersion() == Long.MAX_VALUE) {
					return Future.failedFuture(
						"Invalid expected version for indexer queue reset: "
							+ reset.getExpectedVersion()
					);
				}

				if (indexer.status() != IndexerStatus.AVAILABLE
					|| indexer.mutationState() == MutationState.DELETING) {
					return Future.failedFuture("Cannot reset deleted indexer queue: " + reset.getIndexerId());
				}

				String newQueueName = nextQueueName(
					reset.getExpectedQueueName(),
					reset.getExpectedVersion()
				);
				if (alreadyApplied(indexer, reset, newQueueName)) {
					return completeReset(
						indexer.id(),
						indexer.targetId(),
						indexer.version(),
						reset.getExpectedQueueName()
					);
				}

				if (indexer.version() != reset.getExpectedVersion()
					|| !indexer.queueName().equals(reset.getExpectedQueueName())) {
					return Future.failedFuture(
						"Indexer queue state conflict for id " + indexer.id()
							+ ": expected queue " + reset.getExpectedQueueName()
							+ " at version " + reset.getExpectedVersion()
							+ " but was queue " + indexer.queueName()
							+ " at version " + indexer.version()
					);
				}

				return queueResourceManager.ensure(newQueueName)
					.compose(ignored -> metadataRepository.updateIndexerQueueName(
						new UpdateIndexerQueueName(
							indexer.id(),
							newQueueName,
							reset.getExpectedVersion()
						)
					))
					.compose(ignored -> completeReset(
						indexer.id(),
						indexer.targetId(),
						reset.getExpectedVersion() + 1L,
						reset.getExpectedQueueName()
					));
			});
	}

	private boolean alreadyApplied(
		IndexerRecord indexer,
		ResetIndexerQueueCommand reset,
		String newQueueName
	) {
		return indexer.version() == reset.getExpectedVersion() + 1
			&& indexer.queueName().equals(newQueueName);
	}

	private Future<Void> publish(Integer indexerId, Integer targetId, long version) {
		return metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
			indexerId,
			targetId,
			getType(),
			version
		));
	}

	private Future<Void> completeReset(
		Integer indexerId,
		Integer targetId,
		long version,
		String previousQueueName
	) {
		return publish(indexerId, targetId, version)
			.compose(ignored -> commandService.submit(new CleanupResetIndexerQueueCommand(
				indexerId,
				previousQueueName
			)));
	}

	private String nextQueueName(String queueName, long expectedVersion) {
		Objects.requireNonNull(queueName, "queueName");
		String currentVersionSuffix = "-v" + expectedVersion;
		String baseQueueName = queueName.endsWith(currentVersionSuffix)
			? queueName.substring(0, queueName.length() - currentVersionSuffix.length())
			: queueName;
		long resultingVersion = expectedVersion + 1;
		return baseQueueName + "-v" + resultingVersion;
	}
}
