package com.inqwise.indexer.commands;

import java.util.List;

import com.inqwise.indexer.IndexerActionItem;

public record RoutedIndexActions(
	Integer indexerId,
	long indexerVersion,
	String queueName,
	List<IndexerActionItem> actions,
	boolean metadataChanged
) {
	public RoutedIndexActions(
		Integer indexerId,
		long indexerVersion,
		String queueName,
		List<IndexerActionItem> actions
	) {
		this(indexerId, indexerVersion, queueName, actions, false);
	}

	public RoutedIndexActions {
		actions = List.copyOf(actions);
	}
}
