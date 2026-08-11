package com.inqwise.indexer.adapters.local;

import java.util.Map;
import java.util.Objects;

import com.inqwise.indexer.provisioning.definitions.IndexerDefinition;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinitionRequest;

import io.vertx.core.Future;

public class StaticIndexerDefinitionProvider implements IndexerDefinitionProvider {
	private final IndexerDefinition definition;

	public StaticIndexerDefinitionProvider(IndexerDefinition definition) {
		this.definition = Objects.requireNonNull(definition, "definition");
	}

	@Override
	public Future<IndexerDefinition> get(IndexerDefinitionRequest request) {
		return Future.succeededFuture(definition);
	}

	@Override
	public Future<Map<String, IndexerDefinition>> list() {
		return Future.succeededFuture(Map.of("default", definition));
	}
}
