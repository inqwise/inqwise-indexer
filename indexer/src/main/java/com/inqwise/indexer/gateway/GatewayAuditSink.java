package com.inqwise.indexer.gateway;

import io.vertx.core.Future;

@FunctionalInterface
public interface GatewayAuditSink {
	GatewayAuditSink NOOP = event -> Future.succeededFuture();

	Future<Void> record(GatewayAuditEvent event);
}
