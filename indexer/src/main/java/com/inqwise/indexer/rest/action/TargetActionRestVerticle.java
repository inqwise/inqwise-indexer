package com.inqwise.indexer.rest.action;

import com.inqwise.indexer.service.IndexerErrors;
import com.inqwise.indexer.rest.RestOperations;
import com.inqwise.indexer.service.action.TargetActionService;
import com.inqwise.indexer.service.action.TargetActionServices;
import com.inqwise.indexer.service.action.TargetActionSubmitRequest;
import com.inqwise.indexer.service.action.TargetActionSubmitResult;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public class TargetActionRestVerticle extends AbstractVerticle {
	private final TargetActionRestOptions configuredOptions;
	private HttpServer server;
	private int actualPort = -1;

	public TargetActionRestVerticle() {
		this.configuredOptions = null;
	}

	public TargetActionRestVerticle(TargetActionRestOptions options) {
		this.configuredOptions = options;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		TargetActionRestOptions options = configuredOptions == null
			? new TargetActionRestOptions(config())
			: configuredOptions;
		TargetActionService actionService = TargetActionServices.proxy(vertx);

		OpenAPIContract.from(vertx, options.getOpenApiPath())
			.map(contract -> {
				RouterBuilder builder = RouterBuilder.create(
					vertx,
					contract,
					RequestExtractor.withBodyHandler()
				);
				builder.rootHandler(BodyHandler.create());
				RestOperations.bind(
					builder,
					"submitTargetActions",
					context -> actionService.submit(submitRequest(context)),
					TargetActionRestVerticle::toJson
				);

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

	private static JsonObject toJson(Object result) {
		if (result instanceof TargetActionSubmitResult value) {
			return value.toJson();
		}
		throw IndexerErrors.invalidRequest("Unsupported target action REST result type: " + result.getClass().getName());
	}

	private static TargetActionSubmitRequest submitRequest(RoutingContext context) {
		JsonObject body = context.body().asJsonObject();
		if (body == null) {
			throw IndexerErrors.invalidRequest("Request body is required");
		}

		return TargetActionSubmitRequest.fromJson(body.copy()
			.put(TargetActionSubmitRequest.Keys.TARGET_NAME, context.pathParam("target_name")));
	}
}
