package com.inqwise.indexer.metadata;

public record UpdateManifestStatus(
	Integer id,
	ManifestStatus status,
	long expectedVersion
) {
}
