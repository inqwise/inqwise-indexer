package com.inqwise.indexer.lifecycle;

import io.vertx.core.Future;

public interface IndexerLifecycleSubscription {
	IndexerLifecycleSubscription NOOP = () -> Future.succeededFuture();

	Future<Void> close();
}
