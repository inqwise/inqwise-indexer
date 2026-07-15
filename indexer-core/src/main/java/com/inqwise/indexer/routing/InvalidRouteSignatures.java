package com.inqwise.indexer.routing;

import java.util.List;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.commands.ActionDestination;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;

public final class InvalidRouteSignatures {
	private InvalidRouteSignatures() {
	}

	public static List<InvalidRouteSignature> from(SubmitIndexActionsCommand command) {
		return from(command, null);
	}

	public static List<InvalidRouteSignature> from(
		SubmitIndexActionsCommand command,
		String periodKey
	) {
		return command.getActions().stream()
			.map(action -> from(
				command.getTargetName(),
				periodKey,
				action
			))
			.toList();
	}

	private static InvalidRouteSignature from(
		String targetName,
		String periodKey,
		IndexerActionItem action
	) {
		ActionDestination destination = ActionDestination.from(action);
		boolean hasTargetEnvelope = targetName != null;
		return new InvalidRouteSignature(
			hasTargetEnvelope ? targetName : null,
			hasTargetEnvelope ? periodKey : null,
			hasTargetEnvelope ? null : destination.targetId(),
			hasTargetEnvelope ? null : destination.indexerId(),
			hasTargetEnvelope ? null : destination.indexName(),
			action.getActionType()
		);
	}
}
