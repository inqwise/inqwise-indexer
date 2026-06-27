package com.inqwise.indexer.hot;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

			return new RegistryEntry(
				config.options(),
				new InMemoryTargetInvalidationRegistry(config.options())
			);
		}).registry();
	}

	private record RegistryEntry(
		TargetInvalidationRegistryOptions options,
		TargetInvalidationRegistry registry
	) {
	}
}
