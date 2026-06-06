package com.inqwise.indexer.provisioning;

import com.inqwise.indexer.definitions.IndexDefinition;

import io.vertx.core.Future;

public interface IndexerDocumentIndexResourceManager {
	IndexerDocumentIndexResourceManager NOOP = new IndexerDocumentIndexResourceManager() {
		@Override
		public Future<Void> ensure(String indexName, IndexDefinition definition) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> delete(String indexName) {
			return Future.succeededFuture();
		}
	};

	Future<Void> ensure(String indexName, IndexDefinition definition);

	Future<Void> delete(String indexName);
}
