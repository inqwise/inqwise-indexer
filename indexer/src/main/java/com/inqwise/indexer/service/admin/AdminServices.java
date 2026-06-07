package com.inqwise.indexer.service.admin;

import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;

public final class AdminServices {
	public static final String DEFAULT_ADDRESS = "indexer.service.admin";

	private AdminServices() {
	}

	public static AdminService proxy() {
		return proxy(Vertx.currentContext().owner());
	}

	public static AdminService proxy(Vertx vertx) {
		return new ServiceProxyBuilder(vertx)
			.setAddress(DEFAULT_ADDRESS)
			.build(AdminService.class);
	}
}
