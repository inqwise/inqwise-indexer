package com.inqwise.indexer.providers;

import java.util.List;
import java.util.Optional;

import com.inqwise.indexer.IndexerMarkerHandler;
import com.inqwise.indexer.IndexerModel;

public interface IndexerPlugin {
	default List<IndexerActionReceiveCapability> actionReceiveCapabilities() {
		return List.of();
	}

	default Optional<IndexerMarkerHandler> markerHandler(IndexerModel model) {
		return Optional.empty();
	}
}
