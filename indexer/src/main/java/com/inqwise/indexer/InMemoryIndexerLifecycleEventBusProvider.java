package com.inqwise.indexer;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryIndexerLifecycleEventBusProvider
	implements IndexerLifecycleEventBusProvider {
	private final ConcurrentMap<String, InMemoryIndexerLifecycleEventBus> busesByNamespace =
		new ConcurrentHashMap<>();

	@Override
	public IndexerLifecycleEventBus create(IndexerLifecycleEventBusConfig config) {
		Objects.requireNonNull(config, "config");
		return busesByNamespace.computeIfAbsent(
			config.namespace(),
			ignored -> new InMemoryIndexerLifecycleEventBus()
		);
	}
}
