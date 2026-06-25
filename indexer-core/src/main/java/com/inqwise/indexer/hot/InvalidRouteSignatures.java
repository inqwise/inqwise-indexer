package com.inqwise.indexer.hot;

import java.util.List;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.commands.ActionDestination;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;

public final class InvalidRouteSignatures {
	private InvalidRouteSignatures() {
	}

	public static List<InvalidRouteSignature> from(HotIndexActionsRequest request) {
		return request.actions().stream()
			.map(action -> from(
				request.targetName(),
				action
			))
			.toList();
	}

	public static List<InvalidRouteSignature> from(SubmitIndexActionsCommand command) {
		return command.getActions().stream()
			.map(action -> from(
				command.getTargetName(),
				action
			))
			.toList();
	}

	private static InvalidRouteSignature from(
		String targetName,
		IndexerActionItem action
	) {
		ActionDestination destination = ActionDestination.from(action);
		boolean hasTargetEnvelope = targetName != null;
		return new InvalidRouteSignature(
			hasTargetEnvelope ? targetName : null,
			null,
			hasTargetEnvelope ? null : destination.targetId(),
			hasTargetEnvelope ? null : destination.indexerId(),
			hasTargetEnvelope ? null : destination.indexName(),
			action.getActionType()
		);
	}
}
