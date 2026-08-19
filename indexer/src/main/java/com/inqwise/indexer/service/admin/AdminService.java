package com.inqwise.indexer.service.admin;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

@ProxyGen
@VertxGen
public interface AdminService {
	Future<AdminTargetListResult> listTargets(AdminTargetQuery query);

	Future<AdminIndexerListResult> listIndexers(AdminIndexerQuery query);

	Future<AdminTargetDefinitionListResult> listTargetDefinitions();

	Future<AdminTargetDefinitionResult> getTargetDefinition(String targetName);

	Future<AdminIndexerDefinitionListResult> listIndexerDefinitions();

	Future<AdminIndexerDefinitionResult> getIndexerDefinition(String name);

	Future<AdminInvalidRouteListResult> listInvalidRoutes(int maxRoutes);

	Future<AdminTargetInvalidationListResult> listTargetInvalidations(int maxTargets);

	Future<AdminHotTargetListResult> listHotTargets(int maxTargets);

	Future<AdminNodeStatusResult> nodeStatus();

	Future<AdminNodeStatusResult> recoverNode();

	Future<AdminInfrastructureStatusResult> infrastructureStatus();

	Future<AdminTargetResult> getTarget(AdminTargetGetRequest request);

	Future<AdminIndexerResult> getIndexer(AdminIndexerGetRequest request);

	Future<AdminTargetResult> recoverTargetProvisioning(AdminRecoverTargetProvisioningRequest request);

	Future<AdminIndexerResult> activateIndexer(AdminIndexerLifecycleRequest request);

	Future<AdminIndexerResult> deactivateIndexer(AdminIndexerLifecycleRequest request);

	Future<AdminIndexerResult> deleteIndexer(AdminDeleteIndexerRequest request);

	Future<AdminIndexerResult> resetIndexerQueue(AdminResetIndexerQueueRequest request);

	Future<AdminTargetResult> createTarget(AdminCreateTargetRequest request);

	Future<AdminIndexerResult> createIndexer(AdminCreateIndexerRequest request);
}
