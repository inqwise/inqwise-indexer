package com.inqwise.indexer.runtime;

import com.inqwise.indexer.actions.IndexerActionItem;

import io.vertx.core.Future;

@FunctionalInterface
public interface ActionItemProcessHandler {
	Future<Void> process(IndexerActionItem item);
}
