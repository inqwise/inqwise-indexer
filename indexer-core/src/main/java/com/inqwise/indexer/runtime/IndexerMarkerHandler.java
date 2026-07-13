package com.inqwise.indexer.runtime;

import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.actions.CatchUpBarrierActionItem;
import com.inqwise.indexer.actions.CompleteIndexActionItem;

import io.vertx.core.Future;

public interface IndexerMarkerHandler {
	IndexerMarkerHandler FAILING = new IndexerMarkerHandler() {
		@Override
		public Future<Void> complete(IndexerModel model, CompleteIndexActionItem item) {
			return Future.failedFuture(new UnsupportedOperationException(
				"Complete index action handler is not configured"
			));
		}

		@Override
		public Future<Void> catchUpBarrier(IndexerModel model, CatchUpBarrierActionItem item) {
			return Future.failedFuture(new UnsupportedOperationException(
				"Catch-up barrier action handler is not configured"
			));
		}
	};

	Future<Void> complete(IndexerModel model, CompleteIndexActionItem item);

	Future<Void> catchUpBarrier(IndexerModel model, CatchUpBarrierActionItem item);
}
