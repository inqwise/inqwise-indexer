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
				if (indexer.status() != IndexerStatus.AVAILABLE
					|| indexer.mutationState() == MutationState.DELETING) {
					return Future.failedFuture("Cannot reset deleted indexer queue: " + reset.getIndexerId());
				}

				String newQueueName = nextQueueName(
					reset.getExpectedQueueName(),
					reset.getExpectedVersion()
				);
				if (alreadyApplied(indexer, reset, newQueueName)) {
					return completeReset(indexer, reset.getExpectedQueueName());
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
					.compose(ignored -> metadataRepository.getIndexerById(indexer.id()))
					.compose(updated -> updated
						.map(value -> completeReset(value, reset.getExpectedQueueName()))
						.orElseGet(() -> Future.failedFuture("Indexer not found: " + indexer.id())));
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

	private Future<Void> publish(IndexerRecord indexer) {
		return metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
			indexer.id(),
			indexer.targetId(),
			getType(),
			indexer.version()
		));
	}

	private Future<Void> completeReset(IndexerRecord indexer, String previousQueueName) {
		return publish(indexer)
			.compose(ignored -> commandService.submit(new CleanupResetIndexerQueueCommand(
				indexer.id(),
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
