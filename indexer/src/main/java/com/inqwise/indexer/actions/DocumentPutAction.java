package com.inqwise.indexer.actions;

import com.inqwise.indexer.IndexerActionType;
import com.inqwise.indexer.spi.IndexerActionProvider;

import io.vertx.core.Future;

public class DocumentPutAction implements IndexerAction {
	@Override
	public Future<Void> process() {
		return Future.failedFuture("DocumentPutAction process is not implemented");
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
