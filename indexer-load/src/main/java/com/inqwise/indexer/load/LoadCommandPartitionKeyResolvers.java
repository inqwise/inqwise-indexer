package com.inqwise.indexer.load;

import com.inqwise.indexer.commands.CommandPartitionKey;
import com.inqwise.indexer.commands.CommandPartitionKeyRouter;

public final class LoadCommandPartitionKeyResolvers {
	private LoadCommandPartitionKeyResolvers() {
	}

	public static void registerWith(CommandPartitionKeyRouter router) {
		router
			.register(CreateLoadCommand.TYPE, command -> CommandPartitionKey.targetName(
				command.toJson().getString("target_name")
			))
			.register(StartLoadCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			))
			.register(PublishLoadCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			))
			.register(ApproveLoadPublicationCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			))
			.register(CancelLoadCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			))
			.register(CleanupLoadCommand.TYPE, command -> CommandPartitionKey.indexer(
				command.toJson().getInteger("indexer_id")
			));
	}
}
