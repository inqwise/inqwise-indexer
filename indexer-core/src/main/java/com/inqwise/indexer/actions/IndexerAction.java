package com.inqwise.indexer.actions;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.documents.IndexerDocumentStore;
import com.inqwise.indexer.catalog.indexers.IndexerModel;

import io.vertx.core.Future;

public interface IndexerAction {
	Future<Void> process(IndexerModel model, IndexerDocumentStore documentStore, IndexerActionItem item);
}
