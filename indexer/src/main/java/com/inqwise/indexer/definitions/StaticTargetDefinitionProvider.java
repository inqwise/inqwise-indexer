package com.inqwise.indexer.definitions;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vertx.core.Future;

public class StaticTargetDefinitionProvider implements TargetDefinitionProvider {
	private final Map<String, TargetDefinition> definitionsByName;

	public StaticTargetDefinitionProvider(Collection<TargetDefinition> definitions) {
		this.definitionsByName = Objects.requireNonNull(definitions, "definitions").stream()
			.collect(Collectors.toUnmodifiableMap(
				TargetDefinition::targetName,
				Function.identity()
			));
	}

	@Override
	public Future<Optional<TargetDefinition>> getByName(String targetName) {
		return Future.succeededFuture(Optional.ofNullable(definitionsByName.get(targetName)));
	}
}
