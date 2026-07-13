package com.inqwise.indexer.operations.queues;

import io.vertx.core.Future;

/**
 * Request/reply boundary for operational indexer queue reset. The current reset
 * grouping is accepted as troubleshooting orchestration; service name, method
 * name, and remote envelope remain provisional before external exposure.
 */
public interface IndexerQueueManagementService {
	Future<Void> reset(ResetIndexerQueueRequest request);
}
