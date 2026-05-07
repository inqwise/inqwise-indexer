package com.inqwise.indexer.spi;

import com.inqwise.indexer.IndexerActionType;
import com.inqwise.indexer.actions.IndexerAction;

public interface IndexerActionProvider {
	IndexerAction action();
	IndexerActionType type();
}
