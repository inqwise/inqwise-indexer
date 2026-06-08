package com.inqwise.indexer.rest.runtime;

import java.util.function.Function;

import com.inqwise.indexer.errors.IndexerErrors;
import com.inqwise.indexer.rest.HttpErrorMapper;
import com.inqwise.indexer.service.runtime.RuntimeReconcileRequest;
import com.inqwise.indexer.service.runtime.RuntimeService;
import com.inqwise.indexer.service.runtime.RuntimeServices;
import com.inqwise.indexer.service.runtime.RuntimeStatusResult;

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

public class RuntimeRestVerticle extends AbstractVerticle {
	private final RuntimeRestOptions configuredOptions;
	private HttpServer server;
	private int actualPort = -1;

	public RuntimeRestVerticle() {
		this.configuredOptions = null;
	}

	public RuntimeRestVerticle(RuntimeRestOptions options) {
		this.configuredOptions = options;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		RuntimeRestOptions options = configuredOptions == null
			? new RuntimeRestOptions(config())
			: configuredOptions;
		RuntimeService runtimeService = RuntimeServices.proxy(vertx);

		OpenAPIContract.from(vertx, options.getOpenApiPath())
			.map(contract -> {
				RouterBuilder builder = RouterBuilder.create(
					vertx,
					contract,
					RequestExtractor.withBodyHandler()
				);
				builder.rootHandler(BodyHandler.create());
				bind(builder, "runtimeStatus", context -> runtimeService.status());
				bind(builder, "reconcileIndexer", context -> runtimeService
					.reconcileIndexer(new RuntimeReconcileRequest()
						.setIndexerId(pathInteger(context, "id")))
					.map(new JsonObject().put("status", "ACCEPTED")));

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
		if (result instanceof RuntimeStatusResult value) {
			return value.toJson();
		}
		if (result instanceof JsonObject value) {
			return value;
		}
		throw IndexerErrors.invalidRequest("Unsupported runtime REST result type: " + result.getClass().getName());
	}

	private static Integer pathInteger(RoutingContext context, String name) {
		try {
			return Integer.valueOf(context.pathParam(name));
		} catch (NumberFormatException e) {
			throw IndexerErrors.invalidRequest("Invalid integer value for " + name + ": " + context.pathParam(name));
		}
	}
}
