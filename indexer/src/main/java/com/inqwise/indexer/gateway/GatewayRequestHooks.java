package com.inqwise.indexer.gateway;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

public class GatewayRequestHooks {
	public static final GatewayRequestHooks NOOP = new GatewayRequestHooks();

	public Future<Void> authenticate(RoutingContext context, String operationId) {
		return Future.succeededFuture();
	}

	public Future<Void> authorize(RoutingContext context, String operationId) {
		return Future.succeededFuture();
	}

	public Future<Void> rateLimit(RoutingContext context, String operationId) {
		return Future.succeededFuture();
	}

	public void auditSuccess(RoutingContext context, String operationId) {
	}

	public void auditFailure(RoutingContext context, String operationId, Throwable error) {
	}
}
