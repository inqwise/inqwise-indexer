package com.inqwise.indexer.catalog.targets;

import io.vertx.core.Future;

/**
 * Request/reply boundary for target catalog operations. The current create and
 * provisioning-recovery grouping is accepted; the service name, method names,
 * and remote envelope remain provisional before external exposure.
 */
public interface TargetManagementService {
	Future<TargetManagementResult> createTarget(CreateTargetRequest request);

	Future<TargetManagementResult> recoverProvisioning(RecoverTargetProvisioningRequest request);
}
