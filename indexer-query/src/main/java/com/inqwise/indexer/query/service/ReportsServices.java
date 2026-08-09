package com.inqwise.indexer.query.service;

import java.util.Objects;

import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;

public final class ReportsServices {
	public static final String DEFAULT_ADDRESS = "indexer.query.reports";

	private ReportsServices() {
	}

	public static ReportsService proxy(Vertx vertx) {
		return proxy(vertx, DEFAULT_ADDRESS);
	}

	public static ReportsService proxy(Vertx vertx, String address) {
		return new ServiceProxyBuilder(Objects.requireNonNull(vertx, "vertx"))
			.setAddress(requireAddress(address))
			.build(ReportsService.class);
	}

	public static String address(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			return DEFAULT_ADDRESS;
		}
		return DEFAULT_ADDRESS + "." + namespace.trim();
	}

	static String requireAddress(String address) {
		if (address == null || address.isBlank()) {
			throw new IllegalArgumentException("Reports service address is required");
		}
		return address;
	}
}
