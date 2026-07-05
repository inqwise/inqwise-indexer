package com.inqwise.indexer.management.queues;

import io.vertx.core.Future;

/** Draft boundary; service name, method name, grouping, and placement are provisional. */
public interface IndexerQueueManagementService {
	Future<Void> reset(ResetIndexerQueueRequest request);
}
