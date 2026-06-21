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

	/**
	 * Requests durable deletion of one concrete physical index. Missing resources and
	 * repeated deletion are successful idempotent cleanup misses. Implementations must
	 * reject wildcard, multi-index, and all-index identities and must not expand aliases.
	 */
	Future<Void> delete(String indexName);
}
