package com.inqwise.indexer.catalog.targets;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;

public interface TargetDefinitionProvider {
	Future<Optional<TargetDefinition>> getByName(String targetName);

	Future<List<TargetDefinition>> list();
}
