package com.inqwise.indexer.load;

import com.inqwise.indexer.commands.CommandPartitionKey;
import com.inqwise.indexer.commands.CommandPartitionKeyRouter;

public final class LoadCommandPartitionKeyResolvers {
	private LoadCommandPartitionKeyResolvers() {
	}

	public static void registerWith(CommandPartitionKeyRouter router) {
		router
			.register(StartLoadCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			))
			.register(PublishLoadCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			))
			.register(CleanupLoadCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			));
	}
}
