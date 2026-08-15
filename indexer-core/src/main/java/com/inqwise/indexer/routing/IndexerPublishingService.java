package com.inqwise.indexer.routing;

import java.util.List;

import io.vertx.core.Future;

public interface IndexerPublishingService {
	Future<Void> publish(List<RoutedIndexActions> groups);
}
