package com.inqwise.indexer.provisioning;

import java.util.Objects;

public record ProvisionedIndexer(
	Integer indexerId,
	Integer targetId,
	long version
) {
	public ProvisionedIndexer {
		indexerId = Objects.requireNonNull(indexerId, "indexerId");
		targetId = Objects.requireNonNull(targetId, "targetId");
	}
}
