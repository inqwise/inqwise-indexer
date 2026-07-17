package com.inqwise.indexer.service.target;

import com.inqwise.indexer.catalog.targets.TargetCatalogReader;
import com.inqwise.indexer.catalog.targets.TargetManagementService;
import com.inqwise.indexer.service.ServiceProxyVerticle;

import io.vertx.serviceproxy.ProxyHandler;

public final class TargetCatalogServiceVerticle extends ServiceProxyVerticle<TargetCatalogService> {
	private final TargetCatalogService service;
	private final String address;

	public TargetCatalogServiceVerticle(
		TargetCatalogReader reader,
		TargetManagementService management
	) {
		this(reader, management, TargetCatalogServices.DEFAULT_ADDRESS);
	}

	public TargetCatalogServiceVerticle(
		TargetCatalogReader reader,
		TargetManagementService management,
		String address
	) {
		service = new TargetCatalogServiceImpl(reader, management);
		this.address = TargetCatalogServices.requireAddress(address);
	}

	@Override
	protected String address() {
		return address;
	}

	@Override
	protected TargetCatalogService service() {
		return service;
	}

	@Override
	protected ProxyHandler handler(TargetCatalogService service) {
		return new TargetCatalogServiceVertxProxyHandler(vertx, service);
	}
}
