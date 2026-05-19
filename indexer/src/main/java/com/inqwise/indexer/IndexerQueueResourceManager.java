package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerQueueResourceManager {
	IndexerQueueResourceManager NOOP = new IndexerQueueResourceManager() {
		@Override
		public Future<Void> ensure(String queueName) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> delete(String queueName) {
			return Future.succeededFuture();
		}
	};

	Future<Void> ensure(String queueName);

	Future<Void> delete(String queueName);
}
