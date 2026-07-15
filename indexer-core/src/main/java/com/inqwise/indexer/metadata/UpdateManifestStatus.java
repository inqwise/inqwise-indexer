package com.inqwise.indexer.metadata;

import com.inqwise.indexer.provisioning.ManifestStatus;

public record UpdateManifestStatus(
	Integer id,
	ManifestStatus status,
	long expectedVersion
) {
}
