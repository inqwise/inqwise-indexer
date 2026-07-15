package com.inqwise.indexer.metadata;

import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;

public record UpdateIndexerProvisioningState(
	Integer id,
	IndexerProvisioningState provisioningState,
	long expectedVersion
) {
}
