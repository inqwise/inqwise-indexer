package com.inqwise.indexer.definitions;

import java.util.Optional;

import io.vertx.core.Future;

public interface TargetDefinitionProvider {
	Future<Optional<TargetDefinition>> getByName(String targetName);
}
