package com.inqwise.indexer.service.runtime;

import java.util.Objects;

import com.inqwise.indexer.IndexerRuntime;
import com.inqwise.indexer.IndexerRuntimeReconciler;
import com.inqwise.indexer.service.ServiceProxyVerticle;

import io.vertx.serviceproxy.ProxyHandler;

public class RuntimeServiceVerticle extends ServiceProxyVerticle<RuntimeService> {
	private final RuntimeService service;

	public RuntimeServiceVerticle(
		IndexerRuntime runtime,
		IndexerRuntimeReconciler reconciler
	) {
		this.service = new RuntimeServiceImpl(
			Objects.requireNonNull(runtime, "runtime"),
			Objects.requireNonNull(reconciler, "reconciler")
		);
	}

	@Override
	protected String address() {
		return RuntimeServices.DEFAULT_ADDRESS;
	}

	@Override
	protected RuntimeService service() {
		return service;
	}

	@Override
	protected ProxyHandler handler(RuntimeService service) {
		return new RuntimeServiceVertxProxyHandler(vertx, service);
	}
}
