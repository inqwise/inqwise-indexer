package com.inqwise.indexer.metadata;

import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class InMemoryDocumentStoreMetadataRepositoryDeletionContractTest
	extends DocumentStoreMetadataRepositoryDeletionContract {
	@Override
	DocumentStoreMetadataRepository createRepository() {
		return new InMemoryDocumentStoreMetadataRepository();
	}
}
