package com.inqwise.indexer.service.indexer;

import com.inqwise.indexer.catalog.indexers.IndexerCatalogReader;
import com.inqwise.indexer.catalog.indexers.IndexerManagementService;
import com.inqwise.indexer.service.ServiceProxyVerticle;

import io.vertx.serviceproxy.ProxyHandler;

public final class IndexerCatalogServiceVerticle extends ServiceProxyVerticle<IndexerCatalogService> {
	private final IndexerCatalogService service;
	private final String address;

	public IndexerCatalogServiceVerticle(
		IndexerCatalogReader reader,
		IndexerManagementService management
	) {
		this(reader, management, IndexerCatalogServices.DEFAULT_ADDRESS);
	}

	public IndexerCatalogServiceVerticle(
		IndexerCatalogReader reader,
		IndexerManagementService management,
		String address
	) {
		service = new IndexerCatalogServiceImpl(reader, management);
		this.address = IndexerCatalogServices.requireAddress(address);
	}

	@Override
	protected String address() {
		return address;
	}

	@Override
	protected IndexerCatalogService service() {
		return service;
	}

	@Override
	protected ProxyHandler handler(IndexerCatalogService service) {
		return new IndexerCatalogServiceVertxProxyHandler(vertx, service);
	}
}
