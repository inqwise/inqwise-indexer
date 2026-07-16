package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

public final class TargetDefinitionNotFoundException extends RuntimeException {
	private final String targetName;

	public TargetDefinitionNotFoundException(String targetName) {
		super("Target definition not found by name: " + Objects.requireNonNull(
			targetName,
			"targetName"
		));
		this.targetName = targetName;
	}

	public String targetName() {
		return targetName;
	}
}
