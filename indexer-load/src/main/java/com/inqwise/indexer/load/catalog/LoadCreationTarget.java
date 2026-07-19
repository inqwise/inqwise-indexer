package com.inqwise.indexer.load.catalog;

import java.util.Objects;

public record LoadCreationTarget(
	Integer id,
	String targetName
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private String targetName;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public LoadCreationTarget build() {
			return new LoadCreationTarget(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(targetName, "targetName")
			);
		}
	}
}
