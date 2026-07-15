package com.inqwise.indexer.routing;

import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionType;

public record InvalidRouteSignature(
	String targetName,
	String periodKey,
	Integer targetId,
	Integer indexerId,
	String indexName,
	IndexerActionType actionType
) {
	public InvalidRouteSignature {
		Objects.requireNonNull(actionType, "actionType");
	}
}
