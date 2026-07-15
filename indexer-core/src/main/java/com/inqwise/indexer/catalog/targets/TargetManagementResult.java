package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

public record TargetManagementResult(
	Integer targetId,
	String targetName,
	TargetStatus status,
	TargetProvisioningState provisioningState,
	long version
) {
	public TargetManagementResult {
		targetId = Objects.requireNonNull(targetId, "targetId");
		targetName = Objects.requireNonNull(targetName, "targetName");
		status = Objects.requireNonNull(status, "status");
		provisioningState = Objects.requireNonNull(provisioningState, "provisioningState");
	}
}
