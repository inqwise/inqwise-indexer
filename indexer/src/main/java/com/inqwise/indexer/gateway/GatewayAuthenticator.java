package com.inqwise.indexer.gateway;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

@FunctionalInterface
public interface GatewayAuthenticator {
	GatewayAuthenticator ANONYMOUS = (context, request) ->
		Future.succeededFuture(GatewayPrincipal.builder()
			.withSubject("anonymous")
			.withAuthenticationScheme("none")
			.withAuthenticated(false)
			.build());

	Future<GatewayPrincipal> authenticate(
		RoutingContext context,
		GatewayRequestMetadata request
	);
}
