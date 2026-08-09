package com.inqwise.indexer.example.hn.actions;

import java.util.List;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.actions.RemoveDocumentActionItem;
import com.inqwise.indexer.example.hn.model.HackerNewsDocument;
import com.inqwise.indexer.example.hn.model.HackerNewsDocumentCodec;
import com.inqwise.indexer.service.action.InvalidTargetActionPreparationException;
import com.inqwise.indexer.service.action.TargetActionPreparer;

import io.vertx.core.Future;

public final class HackerNewsTargetActionPreparer implements TargetActionPreparer {
	private static final HackerNewsDocumentCodec DOCUMENT_CODEC =
		new HackerNewsDocumentCodec();

	@Override
	public Future<List<IndexerActionItem>> prepare(List<IndexerActionItem> actions) {
		return Future.succeededFuture(actions.stream().map(this::prepare).toList());
	}

	private IndexerActionItem prepare(IndexerActionItem action) {
		try {
			return switch (action.getActionType()) {
				case PUT_DOCUMENT -> preparePut((PutDocumentActionItem) action);
				case REMOVE_DOCUMENT -> prepareRemove((RemoveDocumentActionItem) action);
				default -> throw new InvalidTargetActionPreparationException(
					"Unsupported Hacker News action: " + action.getActionType()
				);
			};
		} catch (InvalidTargetActionPreparationException error) {
			throw error;
		} catch (IllegalArgumentException | NullPointerException error) {
			throw new InvalidTargetActionPreparationException(
				"Invalid Hacker News action: " + error.getMessage(),
				error
			);
		}
	}

	private IndexerActionItem preparePut(PutDocumentActionItem put) {
		long uid = requireUid(put.getUid());
		HackerNewsDocument document = DOCUMENT_CODEC.decode(put.getDocument());
		if (uid != document.id()) {
			throw new InvalidTargetActionPreparationException(
				"Hacker News action uid must match document id"
			);
		}
		return IndexerActionItems.putDocument(
			put.getUid(),
			DOCUMENT_CODEC.encode(document)
		);
	}

	private IndexerActionItem prepareRemove(RemoveDocumentActionItem remove) {
		requireUid(remove.getUid());
		return IndexerActionItems.removeDocument(remove.getUid());
	}

	private long requireUid(String value) {
		try {
			long uid = Long.parseLong(value);
			if (uid < 1) {
				throw new NumberFormatException("uid must be positive");
			}
			return uid;
		} catch (NumberFormatException error) {
			throw new InvalidTargetActionPreparationException(
				"Hacker News action uid must be a positive item id",
				error
			);
		}
	}
}
