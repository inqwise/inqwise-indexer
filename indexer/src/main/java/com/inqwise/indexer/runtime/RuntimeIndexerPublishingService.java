package com.inqwise.indexer.runtime;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.routing.IndexerPublishingService;
import com.inqwise.indexer.routing.RoutedIndexActions;

import io.vertx.core.Future;

public class RuntimeIndexerPublishingService implements IndexerPublishingService {
	private final IndexerRuntime runtime;

	public RuntimeIndexerPublishingService(IndexerRuntime runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
	}

	@Override
	public Future<Void> publish(List<RoutedIndexActions> groups) {
		Objects.requireNonNull(groups, "groups");
		Future<Void> published = Future.succeededFuture();

		for (RoutedIndexActions group : groups) {
			published = published.compose(ignored -> runtime.publish(group));
		}

		return published;
	}
}
