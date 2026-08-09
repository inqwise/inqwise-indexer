package com.inqwise.indexer.query.service;

import java.util.Objects;

import com.inqwise.indexer.query.ReportExecutionContextResolver;
import com.inqwise.indexer.query.ReportsFacade;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public final class ReportsServiceVerticle extends AbstractVerticle {
	private final ReportsService service;
	private final String address;
	private MessageConsumer<JsonObject> registration;

	public ReportsServiceVerticle(
		ReportsFacade reports,
		ReportExecutionContextResolver contexts
	) {
		this(
			new ReportsServiceImpl(
				Objects.requireNonNull(reports, "reports"),
				Objects.requireNonNull(contexts, "contexts")
			),
			ReportsServices.DEFAULT_ADDRESS
		);
	}

	public ReportsServiceVerticle(
		ReportsFacade reports,
		ReportExecutionContextResolver contexts,
		String address
	) {
		this(
			new ReportsServiceImpl(
				Objects.requireNonNull(reports, "reports"),
				Objects.requireNonNull(contexts, "contexts")
			),
			address
		);
	}

	public ReportsServiceVerticle(
		ReportsFacade reports,
		ReportExecutionContextResolver contexts,
		ReportCaller caller,
		String address
	) {
		this(
			new ReportsServiceImpl(
				Objects.requireNonNull(reports, "reports"),
				Objects.requireNonNull(contexts, "contexts"),
				Objects.requireNonNull(caller, "caller")
			),
			address
		);
	}

	public ReportsServiceVerticle(ReportsService service) {
		this(service, ReportsServices.DEFAULT_ADDRESS);
	}

	public ReportsServiceVerticle(ReportsService service, String address) {
		this.service = Objects.requireNonNull(service, "service");
		this.address = ReportsServices.requireAddress(address);
	}

	@Override
	public void start(Promise<Void> startPromise) {
		registration = new ReportsServiceVertxProxyHandler(vertx, service)
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
