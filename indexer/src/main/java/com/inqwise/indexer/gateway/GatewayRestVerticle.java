package com.inqwise.indexer.gateway;

import java.net.URI;
import java.net.URISyntaxException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class GatewayRestVerticle extends AbstractVerticle {
	private final GatewayRestOptions configuredOptions;
	private HttpServer server;
	private HttpClient client;
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
		client = vertx.createHttpClient();
		Router router = Router.router(vertx);
		router.get("/gateway/status").handler(context -> context.response()
			.putHeader("content-type", "application/json")
			.end(status(options).encode()));
		router.get("/gateway/admin/targets").handler(context ->
			proxyAdminGet(context, options, "/admin/targets"));

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

	private void proxyAdminGet(RoutingContext context, GatewayRestOptions options, String upstreamPath) {
		URI baseUri;
		try {
			baseUri = adminRestBaseUri(options);
		} catch (IllegalArgumentException error) {
			writeJsonError(context, 503, "ADMIN_REST_NOT_CONFIGURED", error.getMessage());
			return;
		}

		String requestUri = upstreamPathWithQuery(context, upstreamPath);
		client.request(HttpMethod.GET, upstreamPort(baseUri), baseUri.getHost(), requestUri)
			.compose(request -> send(request, options))
			.compose(this::bodyWithResponse)
			.onSuccess(upstream -> writeProxyResponse(context, upstream))
			.onFailure(error -> writeJsonError(context, 502, "UPSTREAM_UNAVAILABLE", error.getMessage()));
	}

	private static URI adminRestBaseUri(GatewayRestOptions options) {
		String value = options.getAdminRestBaseUri();
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Admin REST base URI is not configured");
		}

		URI uri;
		try {
			uri = new URI(value);
		} catch (URISyntaxException error) {
			throw new IllegalArgumentException("Admin REST base URI is invalid", error);
		}

		if (!"http".equals(uri.getScheme()) || uri.getHost() == null) {
			throw new IllegalArgumentException("Admin REST base URI must be an http URI with host");
		}

		return uri;
	}

	private static int upstreamPort(URI baseUri) {
		return baseUri.getPort() < 0 ? 80 : baseUri.getPort();
	}

	private static String upstreamPathWithQuery(RoutingContext context, String upstreamPath) {
		String query = context.request().query();
		if (query == null || query.isBlank()) {
			return upstreamPath;
		}

		return upstreamPath + "?" + query;
	}

	private static io.vertx.core.Future<HttpClientResponse> send(
		HttpClientRequest request,
		GatewayRestOptions options
	) {
		return request
			.idleTimeout(options.getRequestTimeoutMs())
			.putHeader("accept", "application/json")
			.send();
	}

	private io.vertx.core.Future<UpstreamResponse> bodyWithResponse(HttpClientResponse response) {
		return response.body()
			.map(body -> new UpstreamResponse(response.statusCode(), response.getHeader("content-type"), body));
	}

	private static void writeProxyResponse(RoutingContext context, UpstreamResponse upstream) {
		if (upstream.contentType() != null) {
			context.response().putHeader("content-type", upstream.contentType());
		}

		context.response()
			.setStatusCode(upstream.statusCode())
			.end(upstream.body());
	}

	private static void writeJsonError(
		RoutingContext context,
		int statusCode,
		String code,
		String message
	) {
		context.response()
			.setStatusCode(statusCode)
			.putHeader("content-type", "application/json")
			.end(new JsonObject()
				.put("error", new JsonObject()
					.put("code", code)
					.put("message", message))
				.encode());
	}

	private void closeClient() {
		if (client != null) {
			client.close();
			client = null;
		}
	}

	private record UpstreamResponse(int statusCode, String contentType, Buffer body) {
	}
}
