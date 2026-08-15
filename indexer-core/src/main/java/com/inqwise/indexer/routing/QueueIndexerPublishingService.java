package com.inqwise.indexer.routing;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.runtime.IndexerQueueClient;
import com.inqwise.indexer.runtime.IndexerQueuePublisher;

import io.vertx.core.Future;

public class QueueIndexerPublishingService implements IndexerPublishingService {
	private final IndexerQueueClient queue;

	public QueueIndexerPublishingService(IndexerQueueClient queue) {
		this.queue = Objects.requireNonNull(queue, "queue");
	}

	@Override
	public Future<Void> publish(List<RoutedIndexActions> groups) {
		Future<Void> published = Future.succeededFuture();

		for (RoutedIndexActions group : groups) {
			published = published.compose(ignored -> publishActions(group.queueName(), group.actions()));
		}

		return published;
	}

	private Future<Void> publishActions(String queueName, List<IndexerActionItem> actions) {
		return queue.publisher(queueName)
			.compose(publisher -> publishActions(publisher, actions)
				.eventually(publisher::close));
	}

	private Future<Void> publishActions(
		IndexerQueuePublisher publisher,
		List<IndexerActionItem> actions
	) {
		List<Future<Void>> publishes = actions.stream()
			.map(publisher::publish)
			.toList();

		return Future.join(publishes).mapEmpty();
	}
}
