package com.inqwise.indexer.service.document;

import java.util.Objects;

import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;

public final class DocumentQueryServices {
	public static final String DEFAULT_ADDRESS = "indexer.service.document-query";

	private DocumentQueryServices() {
	}

	public static DocumentQueryService proxy(Vertx vertx) {
		return proxy(vertx, DEFAULT_ADDRESS);
	}

	public static DocumentQueryService proxy(Vertx vertx, String address) {
		return new ServiceProxyBuilder(Objects.requireNonNull(vertx, "vertx"))
			.setAddress(requireAddress(address))
			.build(DocumentQueryService.class);
	}

	static String requireAddress(String address) {
		if (address == null || address.isBlank()) {
			throw new IllegalArgumentException("Document Query service address is required");
		}
		return address;
	}
}
