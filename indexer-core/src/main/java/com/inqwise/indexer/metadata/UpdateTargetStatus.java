package com.inqwise.indexer.metadata;

public record UpdateTargetStatus(
	Integer id,
	TargetStatus status,
	long expectedVersion
) {
}
