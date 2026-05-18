package com.inqwise.indexer;

import io.vertx.core.Future;

@FunctionalInterface
public interface ActionItemProcessHandler {
	Future<Void> process(IndexerActionItem item);
}
