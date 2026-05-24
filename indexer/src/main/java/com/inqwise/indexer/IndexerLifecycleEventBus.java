package com.inqwise.indexer;

import io.vertx.core.Future;
import io.vertx.core.Handler;

public interface IndexerLifecycleEventBus {
	Future<Void> publish(IndexerMetadataChanged event);

	Future<Void> subscribe(Handler<IndexerMetadataChanged> handler);
}
