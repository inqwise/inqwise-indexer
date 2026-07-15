package com.inqwise.indexer.catalog.indexers;

import io.vertx.core.Future;

/**
 * Request/reply boundary for indexer catalog lifecycle operations. The current
 * activate/deactivate grouping is accepted; standalone physical creation stays
 * in provisioning while the service name, method names, and remote envelope
 * remain provisional before external exposure.
 */
public interface IndexerManagementService {
	Future<IndexerRuntimeStateResult> activate(IndexerRuntimeStateRequest request);

	Future<IndexerRuntimeStateResult> deactivate(IndexerRuntimeStateRequest request);
}
