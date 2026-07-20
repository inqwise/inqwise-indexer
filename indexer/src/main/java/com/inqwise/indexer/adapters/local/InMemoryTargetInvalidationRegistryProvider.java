package com.inqwise.indexer.adapters.local;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.inqwise.indexer.lifecycle.TargetInvalidationRegistry;
import com.inqwise.indexer.hot.TargetInvalidationRegistryConfig;
import com.inqwise.indexer.hot.TargetInvalidationRegistryOptions;
import com.inqwise.indexer.hot.TargetInvalidationRegistryProvider;

public class InMemoryTargetInvalidationRegistryProvider
	implements TargetInvalidationRegistryProvider {
	private final ConcurrentMap<String, RegistryEntry> entriesByNamespace =
		new ConcurrentHashMap<>();

	@Override
	public TargetInvalidationRegistry create(TargetInvalidationRegistryConfig config) {
		Objects.requireNonNull(config, "config");
		return entriesByNamespace.compute(config.namespace(), (ignored, existing) -> {
			if (existing != null) {
				if (!existing.options().equals(config.options())) {
					throw new IllegalArgumentException(
						"Conflicting target invalidation options for namespace: "
							+ config.namespace()
					);
				}
				return existing;
			}

			return RegistryEntry.builder()
				.withOptions(config.options())
				.withRegistry(new InMemoryTargetInvalidationRegistry(config.options()))
				.build();
		}).registry();
	}

	private record RegistryEntry(
		TargetInvalidationRegistryOptions options,
		TargetInvalidationRegistry registry
	) {
		private static Builder builder() {
			return new Builder();
		}

		private static final class Builder {
			private TargetInvalidationRegistryOptions options;
			private TargetInvalidationRegistry registry;

			private Builder withOptions(TargetInvalidationRegistryOptions value) {
				options = value;
				return this;
			}

			private Builder withRegistry(TargetInvalidationRegistry value) {
				registry = value;
				return this;
			}

			private RegistryEntry build() {
				return new RegistryEntry(
					Objects.requireNonNull(options, "options"),
					Objects.requireNonNull(registry, "registry")
				);
			}
		}
	}
}
