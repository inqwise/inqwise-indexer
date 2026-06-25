package com.inqwise.indexer.metadata;

public record UpdateIndexerProvisioningState(
	Integer id,
	IndexerProvisioningState provisioningState,
	long expectedVersion
) {
}
