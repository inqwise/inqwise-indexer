package com.inqwise.indexer.spi;

import com.inqwise.indexer.IndexerActionType;
import com.inqwise.indexer.actions.IndexerAction;
import com.inqwise.indexer.actions.IndexerActionRouter;

public interface IndexerActionProvider {
	IndexerAction action();

	IndexerActionRouter router();

	IndexerActionType type();
}
