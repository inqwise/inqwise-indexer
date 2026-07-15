package com.inqwise.indexer.cleanup;

import com.inqwise.indexer.commands.CommandPartitionKey;
import com.inqwise.indexer.commands.CommandPartitionKeyRouter;

public final class CleanupCommandPartitionKeyResolvers {
	private CleanupCommandPartitionKeyResolvers() {
	}

	public static void registerWith(CommandPartitionKeyRouter router) {
		router
			.register(DeleteIndexerCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			))
			.register(CleanupResetIndexerQueueCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			))
			.register(CleanupDeletingIndexerCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			));
	}
}
