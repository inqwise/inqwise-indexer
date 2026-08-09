package com.inqwise.indexer.runtime;

import com.inqwise.indexer.actions.IndexerActionItem;

import io.vertx.core.Future;

@FunctionalInterface
public interface ActionItemAfterCommitObserver {
	ActionItemAfterCommitObserver NONE = ignored -> Future.succeededFuture();

	Future<Void> afterCommit(IndexerActionItem item);
}
