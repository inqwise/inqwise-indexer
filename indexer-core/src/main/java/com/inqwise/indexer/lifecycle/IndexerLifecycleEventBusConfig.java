package com.inqwise.indexer.lifecycle;

import java.util.Objects;

public record IndexerLifecycleEventBusConfig(String namespace) {
	public IndexerLifecycleEventBusConfig {
		Objects.requireNonNull(namespace, "namespace");
		namespace = namespace.trim();
		if (namespace.isEmpty()) {
			throw new IllegalArgumentException("namespace must not be blank");
		}
	}
}
