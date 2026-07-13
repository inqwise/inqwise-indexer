package com.inqwise.indexer.provisioning;

import com.inqwise.indexer.definitions.QueueDefinition;

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

	default Future<Void> ensure(String queueName, QueueDefinition definition) {
		return ensure(queueName);
	}

	/**
	 * Requests durable deletion of the exact queue identity. Missing resources and
	 * repeated deletion are successful idempotent cleanup misses. Provider failures
	 * propagate to the caller so command orchestration can retry them.
	 */
	Future<Void> delete(String queueName);
}
