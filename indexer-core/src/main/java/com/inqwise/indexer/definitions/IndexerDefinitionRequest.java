package com.inqwise.indexer.definitions;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerType;

public record IndexerDefinitionRequest(
	Integer targetId,
	String targetName,
	IndexerType indexerType,
	IndexerRole role,
	IndexResourceOwnership indexOwnership
) {
}
