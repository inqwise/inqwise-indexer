package com.inqwise.indexer.providers;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerModel;

import io.vertx.core.Future;

public interface IndexerActionReceiveCapability {
	Future<ActionReceiveReadiness> canReceive(IndexerModel indexer, IndexerActionItem action);

	Future<PreparedIndexers> prepareToReceive(PrepareIndexerForActionsRequest request);
}
