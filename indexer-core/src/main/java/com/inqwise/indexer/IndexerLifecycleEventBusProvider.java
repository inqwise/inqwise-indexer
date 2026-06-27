package com.inqwise.indexer;

public interface IndexerLifecycleEventBusProvider {
	/**
	 * Returns a handle to one logical namespace. Equal namespaces share events;
	 * different namespaces must be isolated.
	 */
	IndexerLifecycleEventBus create(IndexerLifecycleEventBusConfig config);
}
