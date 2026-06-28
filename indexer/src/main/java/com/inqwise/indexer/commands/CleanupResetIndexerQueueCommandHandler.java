package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerQueueResourceManager;

import io.vertx.core.Future;

public final class CleanupResetIndexerQueueCommandHandler implements CommandHandler {
	private final IndexerQueueResourceManager queueResources;

	public CleanupResetIndexerQueueCommandHandler(
		IndexerQueueResourceManager queueResources
	) {
		this.queueResources = Objects.requireNonNull(queueResources, "queueResources");
	}

	@Override
	public String getType() {
		return CleanupResetIndexerQueueCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CleanupResetIndexerQueueCommand cleanup =
			new CleanupResetIndexerQueueCommand(command.toJson());
		return queueResources.delete(cleanup.getQueueName());
	}
}
