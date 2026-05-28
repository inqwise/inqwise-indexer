package com.inqwise.indexer;

import io.vertx.core.Future;
import io.vertx.core.Handler;

public interface IndexerLifecycleEventBus {
	IndexerLifecycleEventBus NOOP = new IndexerLifecycleEventBus() {
		@Override
		public Future<Void> publish(IndexerMetadataChanged event) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> subscribe(Handler<IndexerMetadataChanged> handler) {
			return Future.succeededFuture();
		}
	};

	Future<Void> publish(IndexerMetadataChanged event);

	Future<Void> subscribe(Handler<IndexerMetadataChanged> handler);
}
