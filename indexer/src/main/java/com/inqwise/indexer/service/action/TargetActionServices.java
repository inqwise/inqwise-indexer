package com.inqwise.indexer.service.action;

import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;

public final class TargetActionServices {
	public static final String DEFAULT_ADDRESS = "indexer.service.target-action";

	private TargetActionServices() {
	}

	public static TargetActionService proxy() {
		return proxy(Vertx.currentContext().owner());
	}

	public static TargetActionService proxy(Vertx vertx) {
		return new ServiceProxyBuilder(vertx)
			.setAddress(DEFAULT_ADDRESS)
			.build(TargetActionService.class);
	}
}
