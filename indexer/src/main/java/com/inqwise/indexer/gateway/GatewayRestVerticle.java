package com.inqwise.indexer.gateway;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public class GatewayRestVerticle extends AbstractVerticle {
	private static final Logger LOGGER = LogManager.getLogger(GatewayRestVerticle.class);
	private static final String REQUEST_ID_HEADER = "x-request-id";

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
		GatewayRequestMetadata request = requestMetadata(context, operationId);
		GatewayPrincipal[] principal = new GatewayPrincipal[1];
		context.response().putHeader(REQUEST_ID_HEADER, request.requestId());

		authenticate(hooks, context, request)
			.compose(authenticated -> {
				principal[0] = Objects.requireNonNull(
					authenticated,
					"Gateway authenticator returned a null principal"
				);
				return hooks.authorize(request, authenticated);
			})
			.compose(ignored -> hooks.rateLimit(request, principal[0]))
			.compose(ignored -> operation.get())
			.onComplete(result -> {
				GatewayAuditEvent event = auditEvent(request, principal[0], result.cause());
				audit(hooks, event).onComplete(auditResult -> {
					if (auditResult.failed()) {
						LOGGER.error(
							"Gateway audit sink failed for request {}",
							request.requestId(),
							auditResult.cause()
						);
					}
					if (result.failed() && !context.response().ended()) {
						GatewayErrorResponses.requestRejected(context, result.cause());
					}
				});
			});
	}

	private static Future<GatewayPrincipal> authenticate(
		GatewayRequestHooks hooks,
		RoutingContext context,
		GatewayRequestMetadata request
	) {
		try {
			return Objects.requireNonNull(
				hooks.authenticate(context, request),
				"Gateway authenticator returned a null future"
			);
		} catch (Throwable error) {
			return Future.failedFuture(error);
		}
	}

	private static Future<Void> audit(
		GatewayRequestHooks hooks,
		GatewayAuditEvent event
	) {
		try {
			return Objects.requireNonNull(
				hooks.audit(event),
				"Gateway audit sink returned a null future"
			);
		} catch (Throwable error) {
			return Future.failedFuture(error);
		}
	}

	private static GatewayRequestMetadata requestMetadata(
		RoutingContext context,
		String operationId
	) {
		SocketAddress address = context.request().remoteAddress();
		return GatewayRequestMetadata.builder()
			.withRequestId(UUID.randomUUID().toString())
			.withOperationId(operationId)
			.withMethod(context.request().method().name())
			.withPath(context.request().path())
			.withRemoteAddress(address == null ? "unknown" : address.hostAddress())
			.build();
	}

	private static GatewayAuditEvent auditEvent(
		GatewayRequestMetadata request,
		GatewayPrincipal principal,
		Throwable error
	) {
		GatewayAuditEvent.Builder builder = GatewayAuditEvent.builder()
			.withRequest(request)
			.withPrincipal(principal);
		if (error == null) {
			return builder
				.withOutcome(GatewayAuditOutcome.SUCCESS)
				.build();
		}
		return builder
			.withOutcome(GatewayAuditOutcome.FAILURE)
			.withFailureCode(GatewayErrorResponses.auditFailureCode(error))
			.build();
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
