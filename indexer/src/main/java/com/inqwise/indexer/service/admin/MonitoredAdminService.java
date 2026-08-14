package com.inqwise.indexer.service.admin;

import java.util.Objects;
import java.util.function.Supplier;

import com.inqwise.indexer.monitoring.IndexerOperationalMonitor;
import com.inqwise.indexer.monitoring.IndexerOperationalMonitor.LifecycleOperation;

import io.vertx.core.Future;

final class MonitoredAdminService implements AdminService {
	private final AdminService delegate;
	private final IndexerOperationalMonitor monitor;

	MonitoredAdminService(
		AdminService delegate,
		IndexerOperationalMonitor monitor
	) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.monitor = Objects.requireNonNull(monitor, "monitor");
	}

	@Override
	public Future<AdminTargetListResult> listTargets(AdminTargetQuery query) {
		return delegate.listTargets(query);
	}

	@Override
	public Future<AdminIndexerListResult> listIndexers(AdminIndexerQuery query) {
		return delegate.listIndexers(query);
	}

	@Override
	public Future<AdminTargetDefinitionListResult> listTargetDefinitions() {
		return delegate.listTargetDefinitions();
	}

	@Override
	public Future<AdminTargetDefinitionResult> getTargetDefinition(String targetName) {
		return delegate.getTargetDefinition(targetName);
	}

	@Override
	public Future<AdminIndexerDefinitionListResult> listIndexerDefinitions() {
		return delegate.listIndexerDefinitions();
	}

	@Override
	public Future<AdminIndexerDefinitionResult> getIndexerDefinition(String name) {
		return delegate.getIndexerDefinition(name);
	}

	@Override
	public Future<AdminInvalidRouteListResult> listInvalidRoutes(int maxRoutes) {
		return delegate.listInvalidRoutes(maxRoutes);
	}

	@Override
	public Future<AdminTargetInvalidationListResult> listTargetInvalidations(int maxTargets) {
		return delegate.listTargetInvalidations(maxTargets);
	}

	@Override
	public Future<AdminNodeStatusResult> nodeStatus() {
		return delegate.nodeStatus();
	}

	@Override
	public Future<AdminNodeStatusResult> recoverNode() {
		return observe(LifecycleOperation.RECONCILE, delegate::recoverNode);
	}

	@Override
	public Future<AdminInfrastructureStatusResult> infrastructureStatus() {
		return delegate.infrastructureStatus();
	}

	@Override
	public Future<AdminTargetResult> getTarget(AdminTargetGetRequest request) {
		return delegate.getTarget(request);
	}

	@Override
	public Future<AdminIndexerResult> getIndexer(AdminIndexerGetRequest request) {
		return delegate.getIndexer(request);
	}

	@Override
	public Future<AdminTargetResult> recoverTargetProvisioning(
		AdminRecoverTargetProvisioningRequest request
	) {
		return observe(
			LifecycleOperation.RECONCILE,
			() -> delegate.recoverTargetProvisioning(request)
		);
	}

	@Override
	public Future<AdminIndexerResult> activateIndexer(
		AdminIndexerLifecycleRequest request
	) {
		return observe(
			LifecycleOperation.RECONCILE,
			() -> delegate.activateIndexer(request)
		);
	}

	@Override
	public Future<AdminIndexerResult> deactivateIndexer(
		AdminIndexerLifecycleRequest request
	) {
		return observe(
			LifecycleOperation.RECONCILE,
			() -> delegate.deactivateIndexer(request)
		);
	}

	@Override
	public Future<AdminIndexerResult> deleteIndexer(AdminDeleteIndexerRequest request) {
		return observe(LifecycleOperation.DELETE, () -> delegate.deleteIndexer(request));
	}

	@Override
	public Future<AdminIndexerResult> resetIndexerQueue(
		AdminResetIndexerQueueRequest request
	) {
		return observe(
			LifecycleOperation.RESET_QUEUE,
			() -> delegate.resetIndexerQueue(request)
		);
	}

	@Override
	public Future<AdminTargetResult> createTarget(AdminCreateTargetRequest request) {
		return observe(LifecycleOperation.PROVISION, () -> delegate.createTarget(request));
	}

	@Override
	public Future<AdminIndexerResult> createIndexer(AdminCreateIndexerRequest request) {
		return observe(LifecycleOperation.PROVISION, () -> delegate.createIndexer(request));
	}

	private <T> Future<T> observe(
		LifecycleOperation operation,
		Supplier<Future<T>> action
	) {
		monitor.lifecycleStarted(operation);
		Future<T> result;
		try {
			result = action.get();
		} catch (Throwable error) {
			monitor.lifecycleCompleted(operation, false);
			return Future.failedFuture(error);
		}
		result.onComplete(completed ->
			monitor.lifecycleCompleted(operation, completed.succeeded())
		);
		return result;
	}
}
