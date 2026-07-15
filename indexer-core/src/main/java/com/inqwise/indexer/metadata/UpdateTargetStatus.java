package com.inqwise.indexer.metadata;

import com.inqwise.indexer.catalog.targets.TargetStatus;

public record UpdateTargetStatus(
	Integer id,
	TargetStatus status,
	long expectedVersion
) {
}
