package com.inqwise.indexer.cleanup;

import java.util.Objects;

import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;

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
			CleanupResetIndexerQueueCommand.fromJson(command.toJson());
		return queueResources.delete(cleanup.getQueueName());
	}
}
