package com.inqwise.indexer.actions;

public interface IndexerActionProvider {
	IndexerAction action();

	IndexerActionRouter router();

	IndexerActionType type();
}
