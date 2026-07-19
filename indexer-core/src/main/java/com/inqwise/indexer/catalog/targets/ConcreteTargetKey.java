package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

public record ConcreteTargetKey(
	String targetName,
	String periodKey
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String targetName;
		private String periodKey;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withPeriodKey(String value) {
			periodKey = value;
			return this;
		}

		public ConcreteTargetKey build() {
			return new ConcreteTargetKey(
				Objects.requireNonNull(targetName, "targetName"),
				periodKey
			);
		}
	}
}
