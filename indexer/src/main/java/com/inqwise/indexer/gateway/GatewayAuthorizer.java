package com.inqwise.indexer.gateway;

import io.vertx.core.Future;

@FunctionalInterface
public interface GatewayAuthorizer {
	GatewayAuthorizer ALLOW_ALL = (request, principal) -> Future.succeededFuture();

	Future<Void> authorize(
		GatewayRequestMetadata request,
		GatewayPrincipal principal
	);
}
