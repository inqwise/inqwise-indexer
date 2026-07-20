package com.inqwise.indexer.gateway;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
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
			GatewayErrorResponses.adminRestNotConfigured(context);
			return Future.failedFuture(error);
		}

		String requestUri = upstreamPathWithQuery(context, upstreamPath);
		return client.request(HttpMethod.GET, upstreamPort(baseUri), baseUri.getHost(), requestUri)
			.compose(request -> send(request, options))
			.compose(GatewayProxyOperations::bodyWithResponse)
			.compose(upstream -> writeProxyResponse(context, upstream))
			.recover(error -> {
				GatewayErrorResponses.upstreamUnavailable(context);
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
			.map(body -> UpstreamResponse.builder()
				.withStatusCode(response.statusCode())
				.withContentType(response.getHeader("content-type"))
				.withBody(body)
				.build());
	}

	private static Future<Void> writeProxyResponse(RoutingContext context, UpstreamResponse upstream) {
		if (upstream.statusCode() < 200 || upstream.statusCode() >= 300) {
			return GatewayErrorResponses.upstreamFailure(context, upstream.statusCode());
		}

		if (upstream.contentType() != null) {
			context.response().putHeader("content-type", upstream.contentType());
		}

		return context.response()
			.setStatusCode(upstream.statusCode())
			.end(upstream.body());
	}

	private record UpstreamResponse(int statusCode, String contentType, Buffer body) {
		private static Builder builder() {
			return new Builder();
		}

		private static final class Builder {
			private int statusCode;
			private String contentType;
			private Buffer body;

			private Builder withStatusCode(int value) {
				statusCode = value;
				return this;
			}

			private Builder withContentType(String value) {
				contentType = value;
				return this;
			}

			private Builder withBody(Buffer value) {
				body = value == null ? null : value.copy();
				return this;
			}

			private UpstreamResponse build() {
				if (statusCode < 100 || statusCode > 999) {
					throw new IllegalArgumentException("statusCode must be a valid HTTP status");
				}
				return new UpstreamResponse(
					statusCode,
					contentType,
					Objects.requireNonNull(body, "body").copy()
				);
			}
		}
	}
}
