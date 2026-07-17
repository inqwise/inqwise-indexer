package com.inqwise.indexer.service.target;

import java.util.Objects;

import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;

public final class TargetCatalogServices {
	public static final String DEFAULT_ADDRESS = "indexer.service.target-catalog";

	private TargetCatalogServices() {
	}

	public static TargetCatalogService proxy(Vertx vertx) {
		return proxy(vertx, DEFAULT_ADDRESS);
	}

	public static TargetCatalogService proxy(Vertx vertx, String address) {
		return new ServiceProxyBuilder(Objects.requireNonNull(vertx, "vertx"))
			.setAddress(requireAddress(address))
			.build(TargetCatalogService.class);
	}

	public static String address(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			return DEFAULT_ADDRESS;
		}
		return DEFAULT_ADDRESS + "." + namespace.trim();
	}

	static String requireAddress(String address) {
		if (address == null || address.isBlank()) {
			throw new IllegalArgumentException("Target Catalog service address is required");
		}
		return address;
	}
}
