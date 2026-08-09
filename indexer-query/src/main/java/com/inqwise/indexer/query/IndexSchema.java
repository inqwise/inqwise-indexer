package com.inqwise.indexer.query;

import java.util.Objects;

public record IndexSchema(String name, String version) {
	public IndexSchema {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (version == null || version.isBlank()) {
			throw new IllegalArgumentException("version must not be blank");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String name;
		private String version;

		private Builder() {
		}

		public Builder withName(String value) {
			name = value;
			return this;
		}

		public Builder withVersion(String value) {
			version = value;
			return this;
		}

		public IndexSchema build() {
			return new IndexSchema(
				Objects.requireNonNull(name, "name"),
				Objects.requireNonNull(version, "version")
			);
		}
	}
}
