package com.inqwise.indexer.service.document;

import com.inqwise.indexer.documents.DocumentQueryEngine;
import com.inqwise.indexer.service.ServiceProxyVerticle;

import io.vertx.serviceproxy.ProxyHandler;

public final class DocumentQueryServiceVerticle
	extends ServiceProxyVerticle<DocumentQueryService> {
	private final DocumentQueryService service;
	private final String address;

	public DocumentQueryServiceVerticle(DocumentQueryEngine engine) {
		this(engine, DocumentQueryServices.DEFAULT_ADDRESS);
	}

	public DocumentQueryServiceVerticle(DocumentQueryEngine engine, String address) {
		service = new DocumentQueryServiceImpl(engine);
		this.address = DocumentQueryServices.requireAddress(address);
	}

	@Override
	protected String address() {
		return address;
	}

	@Override
	protected DocumentQueryService service() {
		return service;
	}

	@Override
	protected ProxyHandler handler(DocumentQueryService service) {
		return new DocumentQueryServiceVertxProxyHandler(vertx, service);
	}
}
