package com.inqwise.indexer.provisioning.definitions;

import java.util.Map;

import io.vertx.core.Future;

public interface IndexerDefinitionProvider {
	Future<IndexerDefinition> get(IndexerDefinitionRequest request);

	Future<Map<String, IndexerDefinition>> list();
}
