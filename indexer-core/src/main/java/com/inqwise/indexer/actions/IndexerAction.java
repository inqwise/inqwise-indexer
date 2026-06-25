package com.inqwise.indexer.actions;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerDocumentStore;
import com.inqwise.indexer.IndexerModel;

import io.vertx.core.Future;

public interface IndexerAction {
	Future<Void> process(IndexerModel model, IndexerDocumentStore documentStore, IndexerActionItem item);
}
