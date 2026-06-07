package com.inqwise.indexer.service.runtime;

import java.util.Objects;

import com.inqwise.indexer.IndexerRuntime;
import com.inqwise.indexer.service.ServiceProxyVerticle;

import io.vertx.core.Future;
import io.vertx.serviceproxy.ProxyHandler;

public class RuntimeServiceVerticle extends ServiceProxyVerticle<RuntimeService> {
	private final IndexerRuntime runtime;
	private final RuntimeService service;

	public RuntimeServiceVerticle(IndexerRuntime runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.service = new RuntimeServiceImpl(runtime);
	}

	@Override
	protected Future<Void> beforeRegister() {
		return runtime.start();
	}

	@Override
	protected Future<Void> afterUnregister() {
		return runtime.stop();
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
