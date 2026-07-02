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
		public Future<IndexerLifecycleSubscription> subscribe(
			Handler<IndexerMetadataChanged> handler
		) {
			return Future.succeededFuture(IndexerLifecycleSubscription.NOOP);
		}

		@Override
		public Future<IndexerLifecycleSubscription> subscribeTarget(
			Handler<TargetMetadataChanged> handler
		) {
			return Future.succeededFuture(IndexerLifecycleSubscription.NOOP);
		}

		@Override
		public Future<IndexerLifecycleSubscription> subscribeProviderSignals(
			Handler<IndexerLifecycleProviderSignal> handler
		) {
			return Future.succeededFuture(IndexerLifecycleSubscription.NOOP);
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

	/**
	 * Emits a one-way target-cache wake-up. Implementations must observe provider
	 * acceptance failures internally because callers do not await them.
	 */
	default void publishTargetWakeUp(TargetMetadataChanged event) {
		publish(event);
	}

	Future<IndexerLifecycleSubscription> subscribe(Handler<IndexerMetadataChanged> handler);

	Future<IndexerLifecycleSubscription> subscribeTarget(Handler<TargetMetadataChanged> handler);

	Future<IndexerLifecycleSubscription> subscribeProviderSignals(
		Handler<IndexerLifecycleProviderSignal> handler
	);
}
