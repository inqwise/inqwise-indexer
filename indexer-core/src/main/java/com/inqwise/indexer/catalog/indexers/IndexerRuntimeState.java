package com.inqwise.indexer.catalog.indexers;

public enum IndexerRuntimeState {
	ACTIVE,
	NON_ACTIVE;

	public boolean isActive() {
		return this == ACTIVE;
	}
}
