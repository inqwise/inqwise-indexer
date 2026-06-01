package com.inqwise.indexer.actions;

import com.inqwise.indexer.IndexerRole;

public record IndexerActionRouteContext(
	Integer targetId,
	Integer indexerId,
	String targetName,
	String indexName,
	String queueName,
	IndexerRole role
) {
}
