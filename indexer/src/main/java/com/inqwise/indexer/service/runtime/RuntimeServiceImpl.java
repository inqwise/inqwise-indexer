package com.inqwise.indexer.service.runtime;

import java.util.Objects;

import com.inqwise.indexer.IndexerRuntime;
import com.inqwise.indexer.errors.IndexerErrors;

import io.vertx.core.Future;

public class RuntimeServiceImpl implements RuntimeService {
	private final IndexerRuntime runtime;

	public RuntimeServiceImpl(IndexerRuntime runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
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

			return runtime.reconcile(request.getIndexerId())
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}
}
