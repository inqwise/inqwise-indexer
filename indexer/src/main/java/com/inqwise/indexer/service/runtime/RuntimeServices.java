package com.inqwise.indexer.service.runtime;

import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;

public final class RuntimeServices {
	public static final String DEFAULT_ADDRESS = "indexer.service.runtime";

	private RuntimeServices() {
	}

	public static RuntimeService proxy() {
		return proxy(Vertx.currentContext().owner());
	}

	public static RuntimeService proxy(Vertx vertx) {
		return new ServiceProxyBuilder(vertx)
			.setAddress(DEFAULT_ADDRESS)
			.build(RuntimeService.class);
	}
}
