package com.inqwise.indexer.commands;

import java.util.LinkedHashSet;
import java.util.Set;

import com.inqwise.indexer.IndexerActionItem;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

final class CoreCommandPartitionKeyResolvers {
	private CoreCommandPartitionKeyResolvers() {
	}

	static void registerWith(CommandPartitionKeyRouter router) {
		router
			.register(CreateTargetCommand.TYPE, command -> targetName(command, "target_name"))
			.register(CreateIndexerCommand.TYPE, command -> target(command, "target_id"))
			.register(RecoverTargetProvisioningCommand.TYPE, command -> target(command, "target_id"))
			.register(ActivateIndexerCommand.TYPE, command -> indexer(command, "indexer_id"))
			.register(DeactivateIndexerCommand.TYPE, command -> indexer(command, "indexer_id"))
			.register(DeleteIndexerCommand.TYPE, command -> indexer(command, "indexer_id"))
			.register(ResetIndexerQueueCommand.TYPE, command -> indexer(command, "indexer_id"))
			.register(CleanupResetIndexerQueueCommand.TYPE, command -> indexer(command, "indexer_id"))
			.register(CleanupDeletingIndexerCommand.TYPE, command -> indexer(command, "indexer_id"))
			.register(PublishIndexCommand.TYPE, command -> indexer(command, "indexer_id"))
			.register(RetireIndexCommand.TYPE, command -> indexer(command, "indexer_id"))
			.register(MarkIndexReadyCommand.TYPE, command -> publication(command, "publication_id"))
			.register(SubmitIndexActionsCommand.TYPE, CoreCommandPartitionKeyResolvers::actions);
	}

	private static CommandPartitionKey targetName(Command command, String field) {
		return CommandPartitionKey.targetName(command.toJson().getString(field));
	}

	private static CommandPartitionKey target(Command command, String field) {
		return CommandPartitionKey.target(command.toJson().getInteger(field));
	}

	private static CommandPartitionKey indexer(Command command, String field) {
		return CommandPartitionKey.indexer(command.toJson().getInteger(field));
	}

	private static CommandPartitionKey publication(Command command, String field) {
		return CommandPartitionKey.publication(command.toJson().getInteger(field));
	}

	private static CommandPartitionKey actions(Command command) {
		JsonObject payload = command.toJson();
		String targetName = payload.getString("target_name");
		if (targetName != null) {
			return CommandPartitionKey.targetName(targetName);
		}

		Set<Integer> targetIds = new LinkedHashSet<>();
		Set<Integer> indexerIds = new LinkedHashSet<>();
		boolean missingTargetId = false;
		boolean missingIndexerId = false;
		for (Object value : payload.getJsonArray("actions", new JsonArray())) {
			IndexerActionItem action = IndexerActionItem.fromJson((JsonObject) value);
			ActionDestination destination = ActionDestination.from(action);
			if (destination.targetId() == null) {
				missingTargetId = true;
			} else {
				targetIds.add(destination.targetId());
			}
			if (destination.indexerId() == null) {
				missingIndexerId = true;
			} else {
				indexerIds.add(destination.indexerId());
			}
		}

		if (!missingTargetId && targetIds.size() == 1) {
			return CommandPartitionKey.target(targetIds.iterator().next());
		}
		if (!targetIds.isEmpty()) {
			throw invalidActions("Concrete action batch must reference one target id");
		}
		if (!missingIndexerId && indexerIds.size() == 1) {
			return CommandPartitionKey.indexer(indexerIds.iterator().next());
		}
		throw invalidActions("Concrete action batch must reference one indexer id");
	}

	private static CommandFailure invalidActions(String message) {
		return CommandFailure.finalFailure(message);
	}
}
