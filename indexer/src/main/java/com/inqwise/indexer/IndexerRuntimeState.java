package com.inqwise.indexer;

public enum IndexerRuntimeState {
	ACTIVE,
	NON_ACTIVE;

	public boolean isActive() {
		return this == ACTIVE;
	}
}
