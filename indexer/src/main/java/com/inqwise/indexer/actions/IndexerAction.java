package com.inqwise.indexer.actions;

import io.vertx.core.Future;

public interface IndexerAction {
	Future<Void> process();
}
