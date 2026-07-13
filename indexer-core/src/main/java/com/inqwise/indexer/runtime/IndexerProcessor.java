package com.inqwise.indexer.runtime;

import io.vertx.core.Future;

public interface IndexerProcessor {
	Future<Void> open();

	Future<Void> close();
}
