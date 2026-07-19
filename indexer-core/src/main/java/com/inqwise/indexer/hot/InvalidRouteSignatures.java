package com.inqwise.indexer.hot;

import java.util.List;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.routing.ActionDestination;
import com.inqwise.indexer.routing.InvalidRouteSignature;

public final class InvalidRouteSignatures {
	private InvalidRouteSignatures() {
	}

	public static List<InvalidRouteSignature> from(HotIndexActionsRequest request) {
		return from(request, null);
	}

	public static List<InvalidRouteSignature> from(
		HotIndexActionsRequest request,
		String periodKey
	) {
		return request.actions().stream()
			.map(action -> from(
				request.targetName(),
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
		return InvalidRouteSignature.builder()
			.withTargetName(hasTargetEnvelope ? targetName : null)
			.withPeriodKey(hasTargetEnvelope ? periodKey : null)
			.withTargetId(hasTargetEnvelope ? null : destination.targetId())
			.withIndexerId(hasTargetEnvelope ? null : destination.indexerId())
			.withIndexName(hasTargetEnvelope ? null : destination.indexName())
			.withActionType(action.getActionType())
			.build();
	}
}
