package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerProcessor {
	Future<Void> open();

	Future<Void> close();
}
