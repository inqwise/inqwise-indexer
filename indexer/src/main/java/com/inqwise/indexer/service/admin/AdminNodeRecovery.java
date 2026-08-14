package com.inqwise.indexer.service.admin;

import io.vertx.core.Future;

@FunctionalInterface
public interface AdminNodeRecovery {
	AdminNodeRecovery NONE = () -> Future.succeededFuture();

	Future<Void> recover();
}
