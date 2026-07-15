package com.inqwise.indexer.metadata;

import com.inqwise.indexer.catalog.targets.TargetProvisioningState;

public record UpdateTargetProvisioningState(
	Integer id,
	TargetProvisioningState provisioningState,
	long expectedVersion
) {
}
