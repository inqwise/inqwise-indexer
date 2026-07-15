package com.inqwise.indexer.service.runtime;

import java.util.Objects;

import com.inqwise.indexer.runtime.IndexerRuntime;
import com.inqwise.indexer.runtime.IndexerRuntimeReconciler;
import com.inqwise.indexer.service.IndexerErrors;

import io.vertx.core.Future;

public class RuntimeServiceImpl implements RuntimeService {
	private final IndexerRuntime runtime;
	private final IndexerRuntimeReconciler reconciler;

	public RuntimeServiceImpl(IndexerRuntime runtime, IndexerRuntimeReconciler reconciler) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
	}

	@Override
	public Future<RuntimeStatusResult> status() {
		try {
			return Future.succeededFuture(RuntimeStatusResult.from(runtime.snapshots()));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	@Override
	public Future<Void> reconcileIndexer(RuntimeReconcileRequest request) {
		try {
			if (request == null || request.getIndexerId() == null) {
				return Future.failedFuture(IndexerErrors.invalidRequest("Indexer id is required"));
			}

			return reconciler.reconcile(request.getIndexerId())
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}
}
