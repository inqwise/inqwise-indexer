package com.inqwise.indexer.provisioning;

import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

/**
 * Request/reply boundary for creating physical indexer resources and their
 * initial metadata. Catalog lifecycle operations stay in IndexerManagementService.
 */
public interface IndexerProvisioningService {
	Future<IndexerRecord> createIndexer(CreateIndexerProvisioningRequest request);
}
