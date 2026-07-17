package com.inqwise.indexer.load.rest;

import java.util.List;
import java.util.function.Function;

import com.inqwise.indexer.load.service.LoadApprovalRequest;
import com.inqwise.indexer.load.service.LoadCancelRequest;
import com.inqwise.indexer.load.service.LoadCreateRequest;
import com.inqwise.indexer.load.service.LoadResult;
import com.inqwise.indexer.load.service.LoadService;
import com.inqwise.indexer.load.service.LoadServiceErrors;
import com.inqwise.indexer.load.service.LoadServices;
import com.inqwise.indexer.load.service.LoadVersionRequest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public final class LoadRestVerticle extends AbstractVerticle {
	private final LoadRestOptions configuredOptions;
	private HttpServer server;
	private int actualPort = -1;

	public LoadRestVerticle() {
		configuredOptions = null;
	}

	public LoadRestVerticle(LoadRestOptions options) {
		configuredOptions = options;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		LoadRestOptions options = configuredOptions == null
			? new LoadRestOptions(config())
			: configuredOptions;
		LoadService service = LoadServices.proxy(vertx, options.getServiceAddress());

		OpenAPIContract.from(vertx, options.getOpenApiPath())
			.map(contract -> {
				RouterBuilder builder = RouterBuilder.create(
					vertx,
					contract,
					RequestExtractor.withBodyHandler()
				);
				builder.rootHandler(BodyHandler.create());
				bind(builder, "createLoad", context -> service.create(createRequest(context)), 201);
				bind(builder, "startLoad", context -> service.start(versionRequest(context)), 200);
				bind(
					builder,
					"recoverCreatedLoad",
					context -> service.recoverCreated(versionRequest(context)),
					200
				);
				bind(
					builder,
					"approveLoadPublication",
					context -> service.approvePublication(approvalRequest(context)),
					200
				);
				bind(
					builder,
					"cancelLoad",
					context -> service.cancel(cancelRequest(context))
						.map(new JsonObject().put("status", "ACCEPTED")),
					202
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
		server.close().onComplete(stopPromise);
	}

	public int actualPort() {
		return actualPort;
	}

	private static void bind(
		RouterBuilder builder,
		String operationId,
		Function<RoutingContext, Future<?>> handler,
		int successStatus
	) {
		builder.getRoute(operationId)
			.addHandler(context -> {
				try {
					handler.apply(context)
						.onSuccess(result -> write(context, result, successStatus))
						.onFailure(error -> LoadHttpErrorMapper.write(context, error));
				} catch (Throwable error) {
					LoadHttpErrorMapper.write(context, error);
				}
			})
			.addFailureHandler(context -> LoadHttpErrorMapper.write(context, context.failure()));
	}

	private static void write(RoutingContext context, Object result, int status) {
		JsonObject body = result instanceof LoadResult loadResult
			? loadResult.toJson()
			: (JsonObject) result;
		context.response()
			.setStatusCode(status)
			.putHeader("content-type", "application/json")
			.end(body.encode());
	}

	private static LoadCreateRequest createRequest(RoutingContext context) {
		JsonObject body = body(context);
		return new LoadCreateRequest(body);
	}

	private static LoadVersionRequest versionRequest(RoutingContext context) {
		return new LoadVersionRequest()
			.setIndexerId(pathInteger(context, "id"))
			.setExpectedVersion(requiredQueryLong(context, "expected_version"));
	}

	private static LoadApprovalRequest approvalRequest(RoutingContext context) {
		JsonObject body = body(context);
		return new LoadApprovalRequest(body)
			.setIndexerId(pathInteger(context, "id"))
			.setExpectedVersion(requiredQueryLong(context, "expected_version"));
	}

	private static LoadCancelRequest cancelRequest(RoutingContext context) {
		List<String> reasons = context.queryParam("reason");
		return new LoadCancelRequest()
			.setIndexerId(pathInteger(context, "id"))
			.setExpectedVersion(requiredQueryLong(context, "expected_version"))
			.setReason(reasons.isEmpty() ? null : reasons.get(0));
	}

	private static JsonObject body(RoutingContext context) {
		JsonObject body = context.body().asJsonObject();
		if (body == null) {
			throw LoadServiceErrors.invalidRequest("Request body is required");
		}
		return body;
	}

	private static Integer pathInteger(RoutingContext context, String name) {
		try {
			return Integer.valueOf(context.pathParam(name));
		} catch (NumberFormatException error) {
			throw LoadServiceErrors.invalidRequest("Invalid integer value for " + name);
		}
	}

	private static long requiredQueryLong(RoutingContext context, String name) {
		List<String> values = context.queryParam(name);
		if (values.isEmpty()) {
			throw LoadServiceErrors.invalidRequest("Missing required query parameter: " + name);
		}
		try {
			return Long.parseLong(values.get(0));
		} catch (NumberFormatException error) {
			throw LoadServiceErrors.invalidRequest("Invalid long value for " + name);
		}
	}
}
