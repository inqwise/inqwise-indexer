package com.inqwise.indexer.load.service;

import java.util.Objects;

import com.inqwise.indexer.load.api.LoadManagementService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public final class LoadServiceVerticle extends AbstractVerticle {
	private final LoadService service;
	private final String address;
	private MessageConsumer<JsonObject> registration;

	public LoadServiceVerticle(LoadManagementService service) {
		this(service, LoadServices.DEFAULT_ADDRESS);
	}

	public LoadServiceVerticle(LoadManagementService service, String address) {
		this.service = new LoadServiceImpl(Objects.requireNonNull(service, "service"));
		this.address = LoadServices.requireAddress(address);
	}

	@Override
	public void start(Promise<Void> startPromise) {
		registration = new LoadServiceVertxProxyHandler(vertx, service)
			.register(vertx.eventBus(), address);
		startPromise.complete();
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		if (registration == null) {
			stopPromise.complete();
			return;
		}
		MessageConsumer<JsonObject> unregistering = registration;
		registration = null;
		unregistering.unregister().onComplete(stopPromise);
	}
}
