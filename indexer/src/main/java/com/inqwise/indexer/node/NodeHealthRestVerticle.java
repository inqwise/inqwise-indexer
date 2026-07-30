package com.inqwise.indexer.node;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.healthchecks.HealthCheckHandler;

public final class NodeHealthRestVerticle extends AbstractVerticle {
	public static final String LIVE_PATH = "/health/live";
	public static final String READY_PATH = "/health/ready";

	private final NodeHealthRestOptions options;
	private final BooleanSupplier readiness;
	private HttpServer server;
	private int actualPort = -1;

	public NodeHealthRestVerticle(
		NodeHealthRestOptions options,
		BooleanSupplier readiness
	) {
		this.options = Objects.requireNonNull(options, "options");
		this.readiness = Objects.requireNonNull(readiness, "readiness");
	}

	@Override
	public void start(Promise<Void> startPromise) {
		Router router = Router.router(vertx);
		HealthCheckHandler livenessHandler = HealthCheckHandler.create(vertx);
		HealthCheckHandler readinessHandler = HealthCheckHandler.create(vertx)
			.register("node-ready", promise -> promise.complete(
				readiness.getAsBoolean() ? Status.OK() : Status.KO()
			));

		router.get(LIVE_PATH).handler(livenessHandler);
		router.get(READY_PATH).handler(readinessHandler);

		vertx.createHttpServer()
			.requestHandler(router)
			.listen(options.getPort(), options.getHost())
			.onComplete(result -> {
				if (result.succeeded()) {
					server = result.result();
					actualPort = server.actualPort();
					startPromise.complete();
				} else {
					startPromise.fail(result.cause());
				}
			});
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		if (server == null) {
			stopPromise.complete();
			return;
		}
		server.close().onComplete(stopPromise);
	}

	public int actualPort() {
		return actualPort;
	}
}
