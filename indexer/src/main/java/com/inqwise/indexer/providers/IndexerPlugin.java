package com.inqwise.indexer.providers;

import java.util.List;

public interface IndexerPlugin {
	default List<IndexerActionReceiveCapability> actionReceiveCapabilities() {
		return List.of();
	}
}
