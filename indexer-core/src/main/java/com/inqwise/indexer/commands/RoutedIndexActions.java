package com.inqwise.indexer.commands;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;

public record RoutedIndexActions(
	Integer indexerId,
	Integer targetId,
	long indexerVersion,
	String queueName,
	List<IndexerActionItem> actions,
	boolean metadataChanged
) {
	public RoutedIndexActions(
		Integer indexerId,
		Integer targetId,
		long indexerVersion,
		String queueName,
		List<IndexerActionItem> actions
	) {
		this(indexerId, targetId, indexerVersion, queueName, actions, false);
	}

	public RoutedIndexActions {
		Objects.requireNonNull(indexerId, "indexerId");
		Objects.requireNonNull(targetId, "targetId");
		actions = List.copyOf(actions);
	}
}
