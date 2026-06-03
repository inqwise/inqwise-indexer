package com.inqwise.indexer.definitions;

import io.vertx.core.Future;

public interface IndexerDefinitionProvider {
	Future<IndexerDefinition> get(IndexerDefinitionRequest request);
}
