package com.inqwise.indexer.metadata;

import java.util.Objects;

public class TargetNameEncoder {
	public String concreteTargetName(String baseTargetName, TargetPeriod period) {
		Objects.requireNonNull(baseTargetName, "baseTargetName");

		if (period == null || period.strategy() == TargetPeriodStrategy.NONE) {
			return baseTargetName;
		}

		return baseTargetName + "--" + Objects.requireNonNull(period.key(), "periodKey");
	}
}
