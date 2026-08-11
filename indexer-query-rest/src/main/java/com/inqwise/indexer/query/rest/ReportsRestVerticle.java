package com.inqwise.indexer.query.rest;

import java.util.Objects;
import java.util.function.Function;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.query.service.QueryErrorCodes;
import com.inqwise.indexer.query.service.ReportDiscoveryResult;
import com.inqwise.indexer.query.service.ReportDiscoveryService;
import com.inqwise.indexer.query.service.ReportDiscoveryServices;
import com.inqwise.indexer.query.service.ReportExecutionRequest;
import com.inqwise.indexer.query.service.ReportExecutionResult;
import com.inqwise.indexer.query.service.ReportsService;
import com.inqwise.indexer.query.service.ReportsServices;

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

public final class ReportsRestVerticle extends AbstractVerticle {
	private static final long MAX_REQUEST_BODY_BYTES = 65_536;
	private final ReportsRestOptions configuredOptions;
	private HttpServer server;
	private int actualPort = -1;

	public ReportsRestVerticle() {
		configuredOptions = null;
	}

	public ReportsRestVerticle(ReportsRestOptions options) {
		configuredOptions = Objects.requireNonNull(options, "options");
	}

	@Override
	public void start(Promise<Void> startPromise) {
		ReportsRestOptions options = configuredOptions == null
			? ReportsRestOptions.from(config())
			: configuredOptions;
		ReportsService reports = ReportsServices.proxy(vertx, options.reportsAddress());
		ReportDiscoveryService discovery = ReportDiscoveryServices.proxy(
			vertx,
			options.discoveryAddress()
		);

		OpenAPIContract.from(vertx, options.openApiPath())
			.map(contract -> {
				RouterBuilder builder = RouterBuilder.create(
					vertx,
					contract,
					RequestExtractor.withBodyHandler()
				);
				builder.rootHandler(BodyHandler.create()
					.setBodyLimit(MAX_REQUEST_BODY_BYTES));
				bind(
					builder,
					"discoverReports",
					context -> discovery.discover(),
					ReportDiscoveryResult::toJson
				);
				bind(
					builder,
					"executeReport",
					context -> executePresented(discovery, reports, context),
					ReportExecutionResult::getPayload
				);
				return builder.createRouter();
			})
			.compose(router -> vertx.createHttpServer()
				.requestHandler(router)
				.listen(options.port(), options.host()))
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

	private static Future<ReportExecutionResult> executePresented(
		ReportDiscoveryService discovery,
		ReportsService reports,
		RoutingContext context
	) {
		ReportExecutionRequest request = request(context);
		return discovery.discover().compose(result -> {
			boolean presented = result.getReports().stream()
				.anyMatch(report -> report.getName().equals(request.getReportName()));
			if (!presented) {
				return Future.failedFuture(ErrorTicket.builder()
					.withError(QueryErrorCodes.ReportNotFound)
					.withDetails("Report not found: " + request.getReportName())
					.build());
			}
			return reports.execute(request);
		});
	}

	private static ReportExecutionRequest request(RoutingContext context) {
		JsonObject parameters = context.body().asJsonObject();
		if (parameters == null) {
			throw new IllegalArgumentException("Request body is required");
		}
		return ReportExecutionRequest.builder()
			.withReportName(context.pathParam("report_name"))
			.withParameters(parameters)
			.build();
	}

	private static <T> void bind(
		RouterBuilder builder,
		String operationId,
		Function<RoutingContext, Future<T>> handler,
		Function<T, JsonObject> resultMapper
	) {
		builder.getRoute(operationId)
			.addHandler(context -> handle(context, handler, resultMapper))
			.addFailureHandler(context -> ReportsRestErrorMapper.write(
				context,
				context.failure()
			));
	}

	private static <T> void handle(
		RoutingContext context,
		Function<RoutingContext, Future<T>> handler,
		Function<T, JsonObject> resultMapper
	) {
		try {
			handler.apply(context)
				.onSuccess(result -> context.response()
					.setStatusCode(200)
					.putHeader("content-type", "application/json")
					.end(resultMapper.apply(result).encode()))
				.onFailure(error -> ReportsRestErrorMapper.write(context, error));
		} catch (Throwable error) {
			ReportsRestErrorMapper.write(context, error);
		}
	}
}
