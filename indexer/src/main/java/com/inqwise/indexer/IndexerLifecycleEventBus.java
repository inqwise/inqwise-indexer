package com.inqwise.indexer;

import io.vertx.core.Future;
import io.vertx.core.Handler;

public interface IndexerLifecycleEventBus {
	Future<Void> publish(IndexerLifecycleChanged event);

	Future<Void> subscribe(Handler<IndexerLifecycleChanged> handler);
}
