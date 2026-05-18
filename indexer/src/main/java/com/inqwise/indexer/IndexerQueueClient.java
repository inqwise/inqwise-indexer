package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerQueueClient {
	Future<IndexerQueuePublisher> publisher(String queueName);

	Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options);
}
