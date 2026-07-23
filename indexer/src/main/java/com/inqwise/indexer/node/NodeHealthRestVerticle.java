package com.inqwise.indexer.node;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

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
		router.get(LIVE_PATH).handler(context -> context.response()
			.setStatusCode(204)
			.end());
		router.get(READY_PATH).handler(context -> context.response()
			.setStatusCode(readiness.getAsBoolean() ? 204 : 503)
			.end());

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
