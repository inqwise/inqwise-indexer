package com.inqwise.indexer.metadata;

public record UpdateTargetProvisioningState(
	Integer id,
	TargetProvisioningState provisioningState,
	long expectedVersion
) {
}
