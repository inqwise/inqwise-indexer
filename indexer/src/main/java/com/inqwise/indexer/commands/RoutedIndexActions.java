package com.inqwise.indexer.commands;

import java.util.List;

import com.inqwise.indexer.IndexerActionItem;

record RoutedIndexActions(
	Integer indexerId,
	long indexerVersion,
	String queueName,
	List<IndexerActionItem> actions
) {
	RoutedIndexActions {
		actions = List.copyOf(actions);
	}
}
