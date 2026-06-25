package com.inqwise.indexer.metadata;

public record DeleteTarget(
	Integer id,
	long expectedVersion
) {
}
