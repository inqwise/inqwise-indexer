package com.inqwise.indexer.query.service;

import java.util.Objects;

import com.inqwise.indexer.query.ReportCatalog;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public final class ReportDiscoveryServiceVerticle extends AbstractVerticle {
	private final ReportDiscoveryService service;
	private final String address;
	private MessageConsumer<JsonObject> registration;

	public ReportDiscoveryServiceVerticle(ReportCatalog reports) {
		this(
			new ReportDiscoveryServiceImpl(Objects.requireNonNull(reports, "reports")),
			ReportDiscoveryServices.DEFAULT_ADDRESS
		);
	}

	public ReportDiscoveryServiceVerticle(ReportCatalog reports, String address) {
		this(
			new ReportDiscoveryServiceImpl(Objects.requireNonNull(reports, "reports")),
			address
		);
	}

	public ReportDiscoveryServiceVerticle(ReportDiscoveryService service) {
		this(service, ReportDiscoveryServices.DEFAULT_ADDRESS);
	}

	public ReportDiscoveryServiceVerticle(
		ReportDiscoveryService service,
		String address
	) {
		this.service = Objects.requireNonNull(service, "service");
		this.address = ReportDiscoveryServices.requireAddress(address);
	}

	@Override
	public void start(Promise<Void> startPromise) {
		registration = new ReportDiscoveryServiceVertxProxyHandler(vertx, service)
			.register(vertx.eventBus(), address);
		registration.completion().onComplete(startPromise);
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		if (registration == null) {
			stopPromise.complete();
			return;
		}
		MessageConsumer<JsonObject> unregistering = registration;
		registration = null;
		Future<Void> stopped = unregistering.unregister();
		stopped.onComplete(stopPromise);
	}
}
