package com.inqwise.indexer.runtime;

import io.vertx.core.Future;

public interface IndexerEventPublisher {
	IndexerEventPublisher NOOP = event -> Future.succeededFuture();

	Future<Void> publish(IndexerEvent event);
}
