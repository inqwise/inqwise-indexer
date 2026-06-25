package com.inqwise.indexer.metadata;

public record DeletePublication(
	Integer id,
	long expectedVersion
) {
}
