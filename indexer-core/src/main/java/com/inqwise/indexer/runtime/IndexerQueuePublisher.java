package com.inqwise.indexer.runtime;

import com.inqwise.indexer.actions.IndexerActionItem;

import io.vertx.core.Future;

public interface IndexerQueuePublisher {
	Future<Void> publish(IndexerActionItem item);

	Future<Void> close();
}
