package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerEventPublisher {
	IndexerEventPublisher NOOP = event -> Future.succeededFuture();

	Future<Void> publish(IndexerEvent event);
}
