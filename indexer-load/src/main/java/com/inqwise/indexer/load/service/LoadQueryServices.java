package com.inqwise.indexer.load.service;

import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;

public final class LoadQueryServices {
	public static final String DEFAULT_ADDRESS = "indexer.service.load-query";

	private LoadQueryServices() {
	}

	public static LoadQueryService proxy(Vertx vertx, String address) {
		return new ServiceProxyBuilder(vertx)
			.setAddress(requireAddress(address))
			.build(LoadQueryService.class);
	}

	public static String requireAddress(String address) {
		if (address == null || address.isBlank()) {
			throw new IllegalArgumentException("Load query service address is required");
		}
		return address;
	}
}
