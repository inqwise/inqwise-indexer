package com.inqwise.indexer.provisioning;

import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;

import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class InMemoryIndexerDocumentIndexResourceManagerContractTest
	extends IndexerDocumentIndexResourceManagerContract {
	@Override
	protected IndexerDocumentIndexResourceManager createDocumentIndexResourceManager() {
		return new InMemoryIndexerDocumentStore();
	}
}
