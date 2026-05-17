package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerQueue {
	Future<IndexerQueuePublisher> publisher(String queueName);

	Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options);

	Future<Void> close();

	Future<Void> delete();
}
