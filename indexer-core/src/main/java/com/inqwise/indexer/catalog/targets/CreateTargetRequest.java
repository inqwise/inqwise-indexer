package com.inqwise.indexer.catalog.targets;

import java.time.Instant;
import java.util.Objects;

public record CreateTargetRequest(
	String targetName,
	Instant timestamp,
	CreateTargetIndexerRequest createIndexer
) {
	public CreateTargetRequest {
		Objects.requireNonNull(targetName, "targetName");
	}
}
