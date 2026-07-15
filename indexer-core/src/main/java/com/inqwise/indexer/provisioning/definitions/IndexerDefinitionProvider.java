package com.inqwise.indexer.provisioning.definitions;

import io.vertx.core.Future;

public interface IndexerDefinitionProvider {
	Future<IndexerDefinition> get(IndexerDefinitionRequest request);
}
