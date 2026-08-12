package com.inqwise.indexer.load.service;

import java.util.Objects;

import com.inqwise.indexer.load.api.LoadQuery;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;

public final class LoadQueryServiceVerticle extends AbstractVerticle {
	private final LoadQuery query;
	private final String address;
	private MessageConsumer<?> registration;

	public LoadQueryServiceVerticle(LoadQuery query) {
		this(query, LoadQueryServices.DEFAULT_ADDRESS);
	}

	public LoadQueryServiceVerticle(LoadQuery query, String address) {
		this.query = Objects.requireNonNull(query, "query");
		this.address = LoadQueryServices.requireAddress(address);
	}

	@Override
	public void start(Promise<Void> startPromise) {
		registration = new LoadQueryServiceVertxProxyHandler(
			vertx,
			new LoadQueryServiceImpl(query)
		).register(vertx.eventBus(), address);
		startPromise.complete();
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		if (registration == null) {
			stopPromise.complete();
			return;
		}
		registration.unregister().onComplete(stopPromise);
	}
}
