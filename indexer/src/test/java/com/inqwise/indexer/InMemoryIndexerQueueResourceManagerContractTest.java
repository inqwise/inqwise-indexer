package com.inqwise.indexer;

import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class InMemoryIndexerQueueResourceManagerContractTest
	extends IndexerQueueResourceManagerContract {
	@Override
	protected IndexerQueueResourceManager createQueueResourceManager() {
		return new InMemoryIndexerQueue();
	}
}
