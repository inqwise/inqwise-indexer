package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

public final class TargetCatalogConflictException extends RuntimeException {
	private final Integer targetId;

	public TargetCatalogConflictException(Integer targetId, String details) {
		super("Target conflict for id " + Objects.requireNonNull(targetId, "targetId")
			+ ": " + Objects.requireNonNull(details, "details"));
		this.targetId = targetId;
	}

	public Integer targetId() {
		return targetId;
	}
}
