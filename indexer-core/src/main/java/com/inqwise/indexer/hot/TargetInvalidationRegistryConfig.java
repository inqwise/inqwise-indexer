package com.inqwise.indexer.hot;

import java.util.Objects;

public record TargetInvalidationRegistryConfig(
	String namespace,
	TargetInvalidationRegistryOptions options
) {
	public TargetInvalidationRegistryConfig {
		Objects.requireNonNull(namespace, "namespace");
		namespace = namespace.trim();
		if (namespace.isEmpty()) {
			throw new IllegalArgumentException("namespace must not be blank");
		}

		Objects.requireNonNull(options, "options");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String namespace;
		private TargetInvalidationRegistryOptions options;

		private Builder() {
		}

		public Builder withNamespace(String value) {
			namespace = value;
			return this;
		}

		public Builder withOptions(TargetInvalidationRegistryOptions value) {
			options = value;
			return this;
		}

		public TargetInvalidationRegistryConfig build() {
			return new TargetInvalidationRegistryConfig(
				Objects.requireNonNull(namespace, "namespace"),
				Objects.requireNonNull(options, "options")
			);
		}
	}
}
