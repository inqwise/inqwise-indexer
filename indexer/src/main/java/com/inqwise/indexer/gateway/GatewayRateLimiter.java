package com.inqwise.indexer.gateway;

import io.vertx.core.Future;

@FunctionalInterface
public interface GatewayRateLimiter {
	GatewayRateLimiter UNLIMITED = (request, principal) -> Future.succeededFuture();

	Future<Void> acquire(
		GatewayRequestMetadata request,
		GatewayPrincipal principal
	);
}
