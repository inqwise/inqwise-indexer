package com.inqwise.indexer.management.targets;

import java.time.Instant;
import java.util.Objects;

public record CreateTargetRequest(
	String prefix,
	String targetName,
	Instant timestamp,
	CreateTargetIndexerRequest createIndexer
) {
	public CreateTargetRequest {
		Objects.requireNonNull(targetName, "targetName");
	}
}
