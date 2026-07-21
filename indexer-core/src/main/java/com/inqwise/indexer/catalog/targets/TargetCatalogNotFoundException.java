package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

public final class TargetCatalogNotFoundException extends RuntimeException {
	private final Integer targetId;

	public TargetCatalogNotFoundException(Integer targetId) {
		super("Target not found: " + Objects.requireNonNull(targetId, "targetId"));
		this.targetId = targetId;
	}

	public Integer targetId() {
		return targetId;
	}
}
