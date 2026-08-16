package com.inqwise.indexer.routing;

import java.util.LinkedHashSet;
import java.util.Set;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.commands.CommandPartitionKey;
import com.inqwise.indexer.commands.CommandPartitionKeyRouter;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class RoutingCommandPartitionKeyResolvers {
	private RoutingCommandPartitionKeyResolvers() {
	}

	public static void registerWith(CommandPartitionKeyRouter router) {
		router.register(
			SubmitIndexActionsCommand.TYPE,
			RoutingCommandPartitionKeyResolvers::actions
		);
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
			throw invalidActions("Routed action batch must reference one target id");
		}
		if (!missingIndexerId && indexerIds.size() == 1) {
			return CommandPartitionKey.indexer(indexerIds.iterator().next());
		}
		throw invalidActions("Routed action batch must reference one indexer id");
	}

	private static CommandFailure invalidActions(String message) {
		return CommandFailure.finalFailure(message);
	}
}
