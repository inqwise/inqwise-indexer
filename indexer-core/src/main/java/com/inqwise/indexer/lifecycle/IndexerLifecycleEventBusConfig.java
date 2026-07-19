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

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String namespace;

		private Builder() {
		}

		public Builder withNamespace(String value) {
			namespace = value;
			return this;
		}

		public IndexerLifecycleEventBusConfig build() {
			return new IndexerLifecycleEventBusConfig(namespace);
		}
	}
}
