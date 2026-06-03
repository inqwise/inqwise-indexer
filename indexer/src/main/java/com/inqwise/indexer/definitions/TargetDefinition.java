package com.inqwise.indexer.definitions;

import com.inqwise.indexer.metadata.TargetNameValidator;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;

public record TargetDefinition(
	String targetName,
	TargetPeriodStrategy periodStrategy
) {
	public TargetDefinition {
		TargetNameValidator.requireTargetName(targetName);
		periodStrategy = periodStrategy == null ? TargetPeriodStrategy.NONE : periodStrategy;
	}
}
