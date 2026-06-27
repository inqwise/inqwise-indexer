package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerLifecycleSubscription {
	IndexerLifecycleSubscription NOOP = () -> Future.succeededFuture();

	Future<Void> close();
}
