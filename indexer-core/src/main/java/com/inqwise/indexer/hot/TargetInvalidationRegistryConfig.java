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
}
