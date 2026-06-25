package com.inqwise.indexer.definitions;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;

public record IndexerDefinitionRequest(
	Integer targetId,
	String targetName,
	IndexerType indexerType,
	IndexerRole role,
	IndexResourceOwnership indexOwnership
) {
}
