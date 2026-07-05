package com.inqwise.indexer.management.indexers;

import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

/**
 * Draft indexer-management boundary. Its name, methods, grouping, and function
 * placement are provisional until the management-service review is complete.
 */
public interface IndexerManagementService {
	Future<IndexerRecord> activate(IndexerRuntimeStateRequest request);

	Future<IndexerRecord> deactivate(IndexerRuntimeStateRequest request);
}
