package com.inqwise.indexer.service;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHandler;

public abstract class ServiceProxyVerticle<T> extends AbstractVerticle {
	private MessageConsumer<JsonObject> registration;

	@Override
	public void start(Promise<Void> startPromise) {
		beforeRegister()
			.compose(ignored -> registerService())
			.onComplete(startPromise);
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		unregisterService()
			.compose(ignored -> afterUnregister())
			.onComplete(stopPromise);
	}

	protected Future<Void> beforeRegister() {
		return Future.succeededFuture();
	}

	protected Future<Void> afterUnregister() {
		return Future.succeededFuture();
	}

	protected abstract String address();

	protected abstract T service();

	protected abstract ProxyHandler handler(T service);

	private Future<Void> registerService() {
		registration = handler(service()).register(vertx.eventBus(), address());
		return Future.succeededFuture();
	}

	private Future<Void> unregisterService() {
		if (registration == null) {
			return Future.succeededFuture();
		}

		MessageConsumer<JsonObject> unregistering = registration;
		registration = null;
		return unregistering.unregister();
	}
}
