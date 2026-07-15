package com.inqwise.indexer.providers;

import java.util.List;
import java.util.Optional;

import com.inqwise.indexer.catalog.indexers.IndexerModel;

public interface IndexerPlugin {
	default List<IndexerActionReceiveCapability> actionReceiveCapabilities() {
		return List.of();
	}

	default Optional<IndexerMarkerHandler> markerHandler(IndexerModel model) {
		return Optional.empty();
	}
}
