package com.inqwise.indexer.hot;

import java.util.Objects;

import com.inqwise.indexer.IndexerActionType;

public record InvalidRouteSignature(
	String targetUid,
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
