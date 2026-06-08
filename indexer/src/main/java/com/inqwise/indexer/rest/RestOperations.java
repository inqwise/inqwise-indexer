package com.inqwise.indexer.rest;

import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.router.RouterBuilder;

public final class RestOperations {
	private RestOperations() {
	}

	public static <T> void bind(
		RouterBuilder builder,
		String operationId,
		Function<RoutingContext, Future<T>> handler,
		Function<Object, JsonObject> resultMapper
	) {
		builder.getRoute(operationId)
			.addHandler(context -> handle(context, handler, resultMapper))
			.addFailureHandler(context -> HttpErrorMapper.write(context, context.failure()));
	}

	private static <T> void handle(
		RoutingContext context,
		Function<RoutingContext, Future<T>> handler,
		Function<Object, JsonObject> resultMapper
	) {
		try {
			handler.apply(context)
				.onSuccess(result -> writeJson(context, result, resultMapper))
				.onFailure(error -> HttpErrorMapper.write(context, error));
		} catch (Throwable error) {
			HttpErrorMapper.write(context, error);
		}
	}

	private static void writeJson(
		RoutingContext context,
		Object result,
		Function<Object, JsonObject> resultMapper
	) {
		context.response()
			.putHeader("content-type", "application/json")
			.end(resultMapper.apply(result).encode());
	}
}
