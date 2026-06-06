package com.inqwise.indexer.providers;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

public interface IndexerActionReceiveCapability {
	Future<ActionReceiveReadiness> canReceive(IndexerRecord indexer, IndexerActionItem action);

	Future<PreparedIndexers> prepareToReceive(PrepareIndexerForActionsRequest request);
}
