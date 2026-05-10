package com.inqwise.indexer.actions;

import com.inqwise.indexer.IndexerActionType;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerDocumentStore;
import com.inqwise.indexer.IndexerModel;
import com.inqwise.indexer.PutDocumentActionItem;
import com.inqwise.indexer.spi.IndexerActionProvider;

import io.vertx.core.Future;

public class DocumentPutAction implements IndexerAction {
	@Override
	public Future<Void> process(IndexerModel model, IndexerDocumentStore documentStore, IndexerActionItem item) {
		PutDocumentActionItem put = (PutDocumentActionItem) item;
		return documentStore.put(put.getIndexName(), put.getUid(), put.getDocument());
	}

	public static class Provider implements IndexerActionProvider {

		@Override
		public IndexerAction action() {
			return new DocumentPutAction();
		}

		@Override
		public IndexerActionType type() {
			return IndexerActionType.PUT_DOCUMENT;
		}

	}
}
