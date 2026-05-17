package com.inqwise.indexer.metadata;

public record DeleteManifest(
	Integer id,
	long expectedVersion
) {
}
