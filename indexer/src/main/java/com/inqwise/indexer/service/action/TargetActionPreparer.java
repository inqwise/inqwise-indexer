package com.inqwise.indexer.service.action;

import java.util.List;

import com.inqwise.indexer.actions.IndexerActionItem;

import io.vertx.core.Future;

@FunctionalInterface
public interface TargetActionPreparer {
	TargetActionPreparer NONE = actions -> Future.succeededFuture(List.copyOf(actions));

	Future<List<IndexerActionItem>> prepare(List<IndexerActionItem> actions);
}
