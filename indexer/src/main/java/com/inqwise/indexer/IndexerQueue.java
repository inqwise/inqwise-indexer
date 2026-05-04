package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerQueue {
  Future<Void> publish(IndexerActionItem item);

  Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options);
}
