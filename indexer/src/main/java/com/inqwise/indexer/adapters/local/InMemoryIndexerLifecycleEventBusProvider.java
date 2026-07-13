package com.inqwise.indexer.adapters.local;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBusConfig;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBusProvider;

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
