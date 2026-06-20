package com.inqwise.coordination;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

public interface ExclusiveFlowCoordinator {
	<T> Future<T> execute(
		String key,
		Class<T> resultType,
		Handler<Promise<T>> flow
	);
}
