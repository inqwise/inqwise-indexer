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
		public Future<Void> publish(TargetMetadataChanged event) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> subscribe(Handler<IndexerMetadataChanged> handler) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> subscribeTarget(Handler<TargetMetadataChanged> handler) {
			return Future.succeededFuture();
		}
	};

	Future<Void> publish(IndexerMetadataChanged event);

	/**
	 * Emits a one-way reconciliation wake-up. Implementations must observe
	 * provider acceptance failures internally because callers do not await them.
	 */
	default void publishIndexerWakeUp(IndexerMetadataChanged event) {
		publish(event);
	}

	Future<Void> publish(TargetMetadataChanged event);

	Future<Void> subscribe(Handler<IndexerMetadataChanged> handler);

	Future<Void> subscribeTarget(Handler<TargetMetadataChanged> handler);
}
