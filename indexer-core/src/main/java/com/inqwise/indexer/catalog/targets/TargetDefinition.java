package com.inqwise.indexer.catalog.targets;

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

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String targetName;
		private TargetPeriodStrategy periodStrategy;
		private boolean autoProvisionOnWrite;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withPeriodStrategy(TargetPeriodStrategy value) {
			periodStrategy = value;
			return this;
		}

		public Builder withAutoProvisionOnWrite(boolean value) {
			autoProvisionOnWrite = value;
			return this;
		}

		public TargetDefinition build() {
			TargetNameValidator.requireTargetName(targetName);
			return new TargetDefinition(targetName, periodStrategy, autoProvisionOnWrite);
		}
	}
}
