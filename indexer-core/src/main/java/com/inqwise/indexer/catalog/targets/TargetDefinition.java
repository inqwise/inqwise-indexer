package com.inqwise.indexer.catalog.targets;

import com.inqwise.indexer.metadata.TargetNameValidator;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;

public record TargetDefinition(
	String targetName,
	TargetPeriodStrategy periodStrategy,
	boolean autoProvisionOnWrite
) {
	public TargetDefinition(String targetName, TargetPeriodStrategy periodStrategy) {
		this(targetName, periodStrategy, false);
	}

	public TargetDefinition {
		TargetNameValidator.requireTargetName(targetName);
		periodStrategy = periodStrategy == null ? TargetPeriodStrategy.NONE : periodStrategy;
	}
}
