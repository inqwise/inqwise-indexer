package com.inqwise.indexer.service.action;

import java.util.Objects;

import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.service.ServiceProxyVerticle;

import io.vertx.serviceproxy.ProxyHandler;

public class TargetActionServiceVerticle extends ServiceProxyVerticle<TargetActionService> {
	private final TargetActionService service;

	public TargetActionServiceVerticle(HotIndexActionsService hotActions) {
		this.service = new TargetActionServiceImpl(Objects.requireNonNull(hotActions, "hotActions"));
	}

	@Override
	protected String address() {
		return TargetActionServices.DEFAULT_ADDRESS;
	}

	@Override
	protected TargetActionService service() {
		return service;
	}

	@Override
	protected ProxyHandler handler(TargetActionService service) {
		return new TargetActionServiceVertxProxyHandler(vertx, service);
	}
}
