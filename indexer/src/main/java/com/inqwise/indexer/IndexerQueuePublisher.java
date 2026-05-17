package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerQueuePublisher {
	Future<Void> publish(IndexerActionItem item);

	Future<Void> close();
}
