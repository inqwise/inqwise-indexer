package com.inqwise.indexer.gateway;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

public class GatewayBuiltInRequestHooks extends GatewayRequestHooks {
	private final String apiKey;
	private final String apiKeyHeader;
	private final int rateLimitRequests;
	private final long rateLimitWindowMs;
	private final Map<String, RateLimitWindow> rateLimitWindows = new HashMap<>();

	public GatewayBuiltInRequestHooks(GatewayRestOptions options) {
		this.apiKey = options.getApiKey();
		this.apiKeyHeader = options.getApiKeyHeader();
		this.rateLimitRequests = options.getRateLimitRequests();
		this.rateLimitWindowMs = options.getRateLimitWindowMs();
	}

	@Override
	public Future<GatewayPrincipal> authenticate(
		RoutingContext context,
		GatewayRequestMetadata request
	) {
		if (apiKey == null || apiKey.isBlank()) {
			return super.authenticate(context, request);
		}

		String actual = context.request().getHeader(apiKeyHeader);
		if (apiKey.equals(actual)) {
			return Future.succeededFuture(GatewayPrincipal.builder()
				.withSubject("configured-api-key")
				.withAuthenticationScheme("api-key")
				.withAuthenticated(true)
				.build());
		}

		return Future.failedFuture(GatewayErrorResponses.unauthenticated());
	}

	@Override
	public Future<Void> rateLimit(
		GatewayRequestMetadata request,
		GatewayPrincipal principal
	) {
		if (rateLimitRequests <= 0 || rateLimitWindowMs <= 0) {
			return Future.succeededFuture();
		}

		return acquire(request)
			? Future.succeededFuture()
			: Future.failedFuture(GatewayErrorResponses.rateLimited());
	}

	private synchronized boolean acquire(GatewayRequestMetadata request) {
		long now = System.currentTimeMillis();
		String key = rateLimitKey(request);
		RateLimitWindow window = rateLimitWindows.get(key);
		if (window == null || now - window.startedAt >= rateLimitWindowMs) {
			rateLimitWindows.put(key, RateLimitWindow.builder()
				.withStartedAt(now)
				.withCount(1)
				.build());
			return true;
		}

		if (window.count >= rateLimitRequests) {
			return false;
		}

		window.count++;
		return true;
	}

	private static String rateLimitKey(GatewayRequestMetadata request) {
		return request.operationId() + ':' + request.remoteAddress();
	}

	private static final class RateLimitWindow {
		private final long startedAt;
		private int count;

		private RateLimitWindow(long startedAt, int count) {
			this.startedAt = startedAt;
			this.count = count;
		}

		private static Builder builder() {
			return new Builder();
		}

		private static final class Builder {
			private long startedAt;
			private int count;

			private Builder withStartedAt(long value) {
				startedAt = value;
				return this;
			}

			private Builder withCount(int value) {
				count = value;
				return this;
			}

			private RateLimitWindow build() {
				if (count < 0) {
					throw new IllegalArgumentException("count must not be negative");
				}
				return new RateLimitWindow(startedAt, count);
			}
		}
	}
}
