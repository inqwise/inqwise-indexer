package com.inqwise.indexer;

import io.vertx.core.Future;
import io.vertx.core.Handler;

public interface IndexerQueueConsumer {
	IndexerQueueConsumer handler(Handler<IndexerActionItem> handler);

	Future<Void> pause();

	Future<Void> resume();

	Future<Void> commit();

	Future<Void> close();
}
