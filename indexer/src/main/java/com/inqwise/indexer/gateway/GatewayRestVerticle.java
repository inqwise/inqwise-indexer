package com.inqwise.indexer.gateway;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class GatewayRestVerticle extends AbstractVerticle {
	private final GatewayRestOptions configuredOptions;
	private HttpServer server;
	private int actualPort = -1;

	public GatewayRestVerticle() {
		this.configuredOptions = null;
	}

	public GatewayRestVerticle(GatewayRestOptions options) {
		this.configuredOptions = options;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		GatewayRestOptions options = configuredOptions == null
			? new GatewayRestOptions(config())
			: configuredOptions;
		Router router = Router.router(vertx);
		router.get("/gateway/status").handler(context -> context.response()
			.putHeader("content-type", "application/json")
			.end(status(options).encode()));

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

		server.close()
			.onComplete(stopPromise);
	}

	public int actualPort() {
		return actualPort;
	}

	private static JsonObject status(GatewayRestOptions options) {
		return new JsonObject()
			.put("status", "UP")
			.put("admin_rest_configured", options.getAdminRestBaseUri() != null);
	}
}
