package com.inqwise.indexer.catalog.targets;

import java.time.Instant;

public record CreateTargetRequest(
	String targetName,
	Instant timestamp,
	CreateTargetIndexerRequest createIndexer
) {
	public CreateTargetRequest {
		TargetNameValidator.requireTargetName(targetName);
	}
}
