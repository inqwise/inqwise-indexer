package com.inqwise.indexer.management.targets;

import java.util.Objects;

public record RecoverTargetProvisioningRequest(Integer targetId, long expectedVersion) {
	public RecoverTargetProvisioningRequest {
		Objects.requireNonNull(targetId, "targetId");
	}
}
