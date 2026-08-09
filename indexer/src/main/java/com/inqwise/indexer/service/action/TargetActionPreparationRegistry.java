package com.inqwise.indexer.service.action;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;

import io.vertx.core.Future;

public final class TargetActionPreparationRegistry {
	public static final TargetActionPreparationRegistry NONE =
		new TargetActionPreparationRegistry(Map.of());

	private final Map<String, TargetActionPreparer> preparers;

	public TargetActionPreparationRegistry(Map<String, TargetActionPreparer> preparers) {
		this.preparers = Map.copyOf(Objects.requireNonNull(preparers, "preparers"));
		this.preparers.forEach((targetName, preparer) -> {
			if (targetName == null || targetName.isBlank()) {
				throw new IllegalArgumentException("targetName must not be blank");
			}
			Objects.requireNonNull(preparer, "preparer");
		});
	}

	public Future<List<IndexerActionItem>> prepare(
		String targetName,
		List<IndexerActionItem> actions
	) {
		TargetActionPreparer preparer = preparers.getOrDefault(
			targetName,
			TargetActionPreparer.NONE
		);
		return preparer.prepare(List.copyOf(actions)).map(prepared ->
			List.copyOf(Objects.requireNonNull(prepared, "prepared actions"))
		);
	}
}
