package com.inqwise.indexer.lifecycle;

public enum IndexerLifecycleProviderSignal {
	/** Provider delivery resumed after a connection interruption. */
	RECONNECTED,
	/** Provider detected that one or more events could not be delivered. */
	DELIVERY_LOST,
	/** Provider delivery lag crossed its configured safety threshold. */
	EXCESSIVE_LAG
}
