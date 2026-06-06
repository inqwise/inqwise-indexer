package com.inqwise.indexer.definitions;

import java.util.Objects;

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
}
