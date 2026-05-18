package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerQueueResourceCleaner {
	Future<Void> delete(String queueName);
}
