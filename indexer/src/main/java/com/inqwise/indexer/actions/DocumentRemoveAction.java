package com.inqwise.indexer.actions;

import java.util.Optional;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerActionItems;
import com.inqwise.indexer.IndexerActionType;
import com.inqwise.indexer.IndexerDocumentStore;
import com.inqwise.indexer.IndexerModel;
import com.inqwise.indexer.RemoveDocumentActionItem;
import com.inqwise.indexer.spi.IndexerActionProvider;

import io.vertx.core.Future;

public class DocumentRemoveAction implements IndexerAction {
	@Override
	public Future<Void> process(IndexerModel model, IndexerDocumentStore documentStore, IndexerActionItem item) {
		RemoveDocumentActionItem remove = (RemoveDocumentActionItem) item;
		return documentStore.remove(remove.getIndexName(), remove.getUid());
	}

	public static class Provider implements IndexerActionProvider {
		@Override
		public IndexerAction action() {
			return new DocumentRemoveAction();
		}

		@Override
		public IndexerActionType type() {
			return IndexerActionType.REMOVE_DOCUMENT;
		}

		@Override
		public IndexerActionRouter router() {
			return (context, item, mode) -> {
				RemoveDocumentActionItem remove = (RemoveDocumentActionItem) item;
				if (!matches(context, remove, mode)) {
					return Optional.empty();
				}

				return Optional.of(IndexerActionItems.concreteRemoveDocument(
					context.targetId(),
					context.indexerId(),
					context.indexName(),
					remove.getUid()
				));
			};
		}

		private boolean matches(
			IndexerActionRouteContext context,
			RemoveDocumentActionItem item,
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
