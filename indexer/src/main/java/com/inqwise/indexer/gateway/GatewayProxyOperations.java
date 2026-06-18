package com.inqwise.indexer.gateway;

import java.net.URI;
import java.net.URISyntaxException;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

final class GatewayProxyOperations {
	private GatewayProxyOperations() {
	}

	static Future<Void> proxyAdminGet(
		RoutingContext context,
		HttpClient client,
		GatewayRestOptions options,
		String upstreamPath
	) {
		URI baseUri;
		try {
			baseUri = adminRestBaseUri(options);
		} catch (IllegalArgumentException error) {
			writeJsonError(context, 503, "ADMIN_REST_NOT_CONFIGURED", error.getMessage());
			return Future.failedFuture(error);
		}

		String requestUri = upstreamPathWithQuery(context, upstreamPath);
		return client.request(HttpMethod.GET, upstreamPort(baseUri), baseUri.getHost(), requestUri)
			.compose(request -> send(request, options))
			.compose(GatewayProxyOperations::bodyWithResponse)
			.compose(upstream -> writeProxyResponse(context, upstream))
			.recover(error -> {
				writeJsonError(context, 502, "UPSTREAM_UNAVAILABLE", error.getMessage());
				return Future.failedFuture(error);
			});
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

	private static Future<HttpClientResponse> send(
		HttpClientRequest request,
		GatewayRestOptions options
	) {
		return request
			.idleTimeout(options.getRequestTimeoutMs())
			.putHeader("accept", "application/json")
			.send();
	}

	private static Future<UpstreamResponse> bodyWithResponse(HttpClientResponse response) {
		return response.body()
			.map(body -> new UpstreamResponse(response.statusCode(), response.getHeader("content-type"), body));
	}

	private static Future<Void> writeProxyResponse(RoutingContext context, UpstreamResponse upstream) {
		if (upstream.contentType() != null) {
			context.response().putHeader("content-type", upstream.contentType());
		}

		return context.response()
			.setStatusCode(upstream.statusCode())
			.end(upstream.body());
	}

	static void writeJsonError(
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

	private record UpstreamResponse(int statusCode, String contentType, Buffer body) {
	}
}
