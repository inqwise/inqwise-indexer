package com.inqwise.indexer.provisioning;

import io.vertx.core.Future;

/**
 * Request/reply boundary for creating physical indexer resources and their
 * initial metadata. Catalog lifecycle operations stay in IndexerManagementService.
 */
public interface IndexerProvisioningService {
	Future<ProvisionedIndexer> createIndexer(CreateIndexerProvisioningRequest request);
}
