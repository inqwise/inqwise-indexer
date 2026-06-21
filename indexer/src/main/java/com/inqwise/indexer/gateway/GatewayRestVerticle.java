package com.inqwise.indexer.gateway;

import java.util.function.Supplier;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public class GatewayRestVerticle extends AbstractVerticle {
	private final GatewayRestOptions configuredOptions;
	private final GatewayRequestHooks configuredHooks;
	private HttpServer server;
	private HttpClient client;
	private int actualPort = -1;

	public GatewayRestVerticle() {
		this.configuredOptions = null;
		this.configuredHooks = null;
	}

	public GatewayRestVerticle(GatewayRestOptions options) {
		this.configuredOptions = options;
		this.configuredHooks = null;
	}

	public GatewayRestVerticle(GatewayRestOptions options, GatewayRequestHooks hooks) {
		this.configuredOptions = options;
		this.configuredHooks = hooks;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		GatewayRestOptions options = configuredOptions == null
			? new GatewayRestOptions(config())
			: configuredOptions;
		GatewayRequestHooks hooks = configuredHooks == null
			? hooks(options)
			: configuredHooks;
		client = vertx.createHttpClient();

		OpenAPIContract.from(vertx, options.getOpenApiPath())
			.map(contract -> {
				RouterBuilder builder = RouterBuilder.create(
					vertx,
					contract,
					RequestExtractor.withBodyHandler()
				);
				builder.rootHandler(BodyHandler.create());
				builder.getRoute("gatewayStatus")
					.addHandler(context -> handle(
						context,
						hooks,
						"gatewayStatus",
						() -> writeJson(context, status(options))
					));
				builder.getRoute("gatewayListTargets")
					.addHandler(context -> handle(
						context,
						hooks,
						"gatewayListTargets",
						() -> GatewayProxyOperations.proxyAdminGet(
							context,
							client,
							options,
							"/admin/targets"
						)
					));
				builder.getRoute("gatewayListIndexers")
					.addHandler(context -> handle(
						context,
						hooks,
						"gatewayListIndexers",
						() -> GatewayProxyOperations.proxyAdminGet(
							context,
							client,
							options,
							"/admin/indexers"
						)
					));

				return builder.createRouter();
			})
			.compose(router -> vertx.createHttpServer()
				.requestHandler(router)
				.listen(options.getPort(), options.getHost()))
			.onComplete(result -> {
				if (result.succeeded()) {
					server = result.result();
					actualPort = server.actualPort();
					startPromise.complete();
				} else {
					closeClient();
					startPromise.fail(result.cause());
				}
			});
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		if (server == null) {
			closeClient();
			stopPromise.complete();
			return;
		}

		server.close()
			.onComplete(result -> {
				closeClient();
				stopPromise.handle(result);
			});
	}

	public int actualPort() {
		return actualPort;
	}

	private static JsonObject status(GatewayRestOptions options) {
		return new JsonObject()
			.put("status", "UP")
			.put("admin_rest_configured", options.getAdminRestBaseUri() != null);
	}

	private static GatewayRequestHooks hooks(GatewayRestOptions options) {
		if ((options.getApiKey() == null || options.getApiKey().isBlank())
			&& options.getRateLimitRequests() <= 0) {
			return GatewayRequestHooks.NOOP;
		}

		return new GatewayBuiltInRequestHooks(options);
	}

	private void handle(
		RoutingContext context,
		GatewayRequestHooks hooks,
		String operationId,
		Supplier<Future<Void>> operation
	) {
		hooks.authenticate(context, operationId)
			.compose(ignored -> hooks.authorize(context, operationId))
			.compose(ignored -> hooks.rateLimit(context, operationId))
			.compose(ignored -> operation.get())
			.onSuccess(ignored -> hooks.auditSuccess(context, operationId))
			.onFailure(error -> {
				hooks.auditFailure(context, operationId, error);
				if (!context.response().ended()) {
					GatewayErrorResponses.requestRejected(context, error);
				}
			});
	}

	private static Future<Void> writeJson(RoutingContext context, JsonObject body) {
		return context.response()
			.putHeader("content-type", "application/json")
			.end(body.encode());
	}

	private void closeClient() {
		if (client != null) {
			client.close();
			client = null;
		}
	}
}
