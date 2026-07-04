package com.inqwise.indexer.management.targets;

import com.inqwise.indexer.metadata.TargetRecord;

import io.vertx.core.Future;

/**
 * Draft request/reply boundary for target management. The service name, method
 * names, method grouping, and placement of each function are refactoring candidates
 * until management ownership and transport are finalized.
 */
public interface TargetManagementService {
	Future<TargetRecord> createTarget(CreateTargetRequest request);

	Future<TargetRecord> recoverProvisioning(RecoverTargetProvisioningRequest request);
}
