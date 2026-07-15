package com.inqwise.indexer.actions;

import java.util.Optional;

import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.documents.IndexerDocumentStore;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.actions.PutDocumentActionItem;

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

		@Override
		public IndexerActionRouter router() {
			return (context, item, mode) -> {
				PutDocumentActionItem put = (PutDocumentActionItem) item;
				if (!matches(context, put, mode)) {
					return Optional.empty();
				}

				return Optional.of(IndexerActionItems.concretePutDocument(
					context.targetId(),
					context.indexerId(),
					context.indexName(),
					put.getUid(),
					put.getDocument()
				));
			};
		}

		private boolean matches(
			IndexerActionRouteContext context,
			PutDocumentActionItem item,
			IndexerActionRouteMode mode
		) {
			if (item.getTargetId() != null && !item.getTargetId().equals(context.targetId())) {
				return failOrSkip("Action target id mismatch", mode);
			}

			if (item.getIndexerId() != null && !item.getIndexerId().equals(context.indexerId())) {
				return failOrSkip("Action indexer id mismatch", mode);
			}

			if (item.getIndexName() != null && !item.getIndexName().equals(context.indexName())) {
				return failOrSkip("Action index mismatch", mode);
			}

			return true;
		}

		private boolean failOrSkip(String message, IndexerActionRouteMode mode) {
			if (mode == IndexerActionRouteMode.DIRECT) {
				throw new IllegalArgumentException(message);
			}

			return false;
		}

	}
}
