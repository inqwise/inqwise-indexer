package com.inqwise.indexer.rest.action;

import java.util.function.Function;

import com.inqwise.indexer.errors.IndexerErrors;
import com.inqwise.indexer.rest.HttpErrorMapper;
import com.inqwise.indexer.service.action.TargetActionService;
import com.inqwise.indexer.service.action.TargetActionServices;
import com.inqwise.indexer.service.action.TargetActionSubmitRequest;
import com.inqwise.indexer.service.action.TargetActionSubmitResult;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
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
				bind(builder, "submitTargetActions", context ->
					actionService.submit(submitRequest(context)));

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

	private static <T> void bind(
		RouterBuilder builder,
		String operationId,
		Function<RoutingContext, Future<T>> handler
	) {
		builder.getRoute(operationId)
			.addHandler(context -> handle(context, handler))
			.addFailureHandler(context -> HttpErrorMapper.write(context, context.failure()));
	}

	private static <T> void handle(RoutingContext context, Function<RoutingContext, Future<T>> handler) {
		try {
			handler.apply(context)
				.onSuccess(result -> writeJson(context, result))
				.onFailure(error -> HttpErrorMapper.write(context, error));
		} catch (Throwable error) {
			HttpErrorMapper.write(context, error);
		}
	}

	private static void writeJson(RoutingContext context, Object result) {
		context.response()
			.putHeader("content-type", "application/json")
			.end(toJson(result).encode());
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

		return new TargetActionSubmitRequest(body.copy()
			.put(TargetActionSubmitRequest.Keys.TARGET_NAME, context.pathParam("target_name")));
	}
}
