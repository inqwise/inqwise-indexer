package com.inqwise.indexer.load.service;

import java.util.Objects;

import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;

public final class LoadServices {
	public static final String DEFAULT_ADDRESS = "indexer.service.load";

	private LoadServices() {
	}

	public static LoadService proxy(Vertx vertx) {
		return proxy(vertx, DEFAULT_ADDRESS);
	}

	public static LoadService proxy(Vertx vertx, String address) {
		return new ServiceProxyBuilder(Objects.requireNonNull(vertx, "vertx"))
			.setAddress(requireAddress(address))
			.build(LoadService.class);
	}

	public static String address(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			return DEFAULT_ADDRESS;
		}
		return DEFAULT_ADDRESS + "." + namespace.trim();
	}

	static String requireAddress(String address) {
		if (address == null || address.isBlank()) {
			throw new IllegalArgumentException("Load service address is required");
		}
		return address;
	}
}
