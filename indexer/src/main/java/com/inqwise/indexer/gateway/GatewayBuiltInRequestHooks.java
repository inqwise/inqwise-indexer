package com.inqwise.indexer.gateway;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;
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
	public Future<Void> authenticate(RoutingContext context, String operationId) {
		if (apiKey == null || apiKey.isBlank()) {
			return Future.succeededFuture();
		}

		String actual = context.request().getHeader(apiKeyHeader);
		if (apiKey.equals(actual)) {
			return Future.succeededFuture();
		}

		return Future.failedFuture(GatewayErrorResponses.unauthenticated());
	}

	@Override
	public Future<Void> rateLimit(RoutingContext context, String operationId) {
		if (rateLimitRequests <= 0 || rateLimitWindowMs <= 0) {
			return Future.succeededFuture();
		}

		return acquire(context, operationId)
			? Future.succeededFuture()
			: Future.failedFuture(GatewayErrorResponses.rateLimited());
	}

	private synchronized boolean acquire(RoutingContext context, String operationId) {
		long now = System.currentTimeMillis();
		String key = rateLimitKey(context, operationId);
		RateLimitWindow window = rateLimitWindows.get(key);
		if (window == null || now - window.startedAt >= rateLimitWindowMs) {
			rateLimitWindows.put(key, new RateLimitWindow(now, 1));
			return true;
		}

		if (window.count >= rateLimitRequests) {
			return false;
		}

		window.count++;
		return true;
	}

	private static String rateLimitKey(RoutingContext context, String operationId) {
		SocketAddress address = context.request().remoteAddress();
		String host = address == null ? "unknown" : address.hostAddress();
		return operationId + ':' + host;
	}

	private static final class RateLimitWindow {
		private final long startedAt;
		private int count;

		private RateLimitWindow(long startedAt, int count) {
			this.startedAt = startedAt;
			this.count = count;
		}
	}
}
